"""Exercise sessionless smoke requests, readiness, parsing and failure handling."""
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class SmokeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        folder = Path(self.temp.name)
        binary = folder / "bin"
        binary.mkdir()
        for name in ("curl", "docker"):
            mock = binary / name
            mock.write_text('''#!/usr/bin/env bash
echo "$(basename "$0") $*" >> "$MOCK_LOG"
if [[ $(basename "$0") == docker ]]; then
  [[ ${MOCK_DB_NETWORK_FAIL:-0} == 0 ]] || exit 7
  printf '%s\\n%s' "$MOCK_READINESS_BODY" "$MOCK_READINESS_STATUS"
else
  [[ ${MOCK_NETWORK_FAIL:-0} == 0 ]] || exit 7
  printf '%s\\n%s' "$MOCK_BODY" "$MOCK_STATUS"
fi
''', encoding="utf-8", newline="\n")
            mock.chmod(0o755)
        python = binary / "python3"
        python.write_text("#!/usr/bin/env bash\nexec " + shlex.quote(Path(sys.executable).as_posix()) + ' "$@"\n', encoding="utf-8", newline="\n")
        python.chmod(0o755)
        self.log = folder / "requests"
        self.env = dict(os.environ,
                        SMOKE_BASE_URL="https://smoke.invalid",
                        MOCK_BIN=binary.as_posix(), MOCK_LOG=self.log.as_posix(),
                        MOCK_BODY=json.dumps({"isSuccess": True, "result": {
                            "headerName": "X-CSRF-TOKEN", "token": "private-test-token"}}),
                        MOCK_STATUS="200", MOCK_READINESS_BODY='{"status":"UP"}',
                        MOCK_READINESS_STATUS="200")
        self.env.pop("SMOKE_COOKIE_FILE", None)
        self.env.pop("SMOKE_TARGET_PORT", None)

    def run_smoke(self):
        command = '''mock_bin=$MOCK_BIN
if command -v cygpath >/dev/null; then mock_bin=$(cygpath -u "$mock_bin"); fi
export PATH="$mock_bin:$PATH"
[[ $(command -v curl) == "$mock_bin/curl" ]] || exit 2
exec bash scripts/ops/smoke.sh'''
        return subprocess.run([os.environ.get("BASH_EXECUTABLE", "bash"), "-c", command],
                              cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)

    def test_public_probe_without_cookie_configuration(self):
        result = self.run_smoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "Smoke passed: csrf\n")
        requests = self.log.read_text()
        self.assertIn("https://smoke.invalid/api/v1/auth/csrf", requests)
        self.assertNotIn("--cookie", requests)
        self.assertNotIn("private-test-token", result.stdout + result.stderr)

    def test_stale_cookie_configuration_is_ignored(self):
        self.env["SMOKE_COOKIE_FILE"] = "/does/not/exist/expired.cookies"
        self.assertEqual(self.run_smoke().returncode, 0)

    def test_each_candidate_checks_its_own_internal_readiness_before_csrf(self):
        for port, slot in (("8080", "blue"), ("8081", "green")):
            with self.subTest(port=port):
                self.env["SMOKE_TARGET_PORT"] = port
                self.log.write_text("")
                result = self.run_smoke()
                self.assertEqual(result.returncode, 0, result.stderr)
                requests = self.log.read_text().splitlines()
                self.assertIn("docker exec backend-" + slot + " curl", requests[0])
                self.assertIn("http://127.0.0.1:9090/actuator/health/readiness", requests[0])
                self.assertIn("http://127.0.0.1:" + port + "/api/v1/auth/csrf", requests[1])
                self.assertIn("Host: smoke.invalid", requests[1])
                self.assertIn("X-Forwarded-Proto: https", requests[1])
                self.assertNotIn("--cookie", "\n".join(requests))

    def test_down_db_readiness_blocks_candidate(self):
        self.env["SMOKE_TARGET_PORT"] = "8081"
        for body, status, network_fail in (("{\"status\":\"DOWN\"}", "503", "0"),
                                          ("{\"status\":\"DOWN\"}", "200", "0"),
                                          ("{}", "200", "1")):
            with self.subTest(body=body, status=status):
                self.log.write_text("")
                self.env.update(MOCK_READINESS_BODY=body, MOCK_READINESS_STATUS=status,
                                MOCK_DB_NETWORK_FAIL=network_fail)
                self.assertNotEqual(self.run_smoke().returncode, 0)
                self.assertNotIn("/api/v1/auth/csrf", self.log.read_text())

    def test_invalid_http_status_is_rejected(self):
        for status in ("302", "401", "500"):
            with self.subTest(status=status):
                self.env["MOCK_STATUS"] = status
                result = self.run_smoke()
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("private-test-token", result.stdout + result.stderr)
                self.assertNotIn("Traceback", result.stderr)

    def test_invalid_csrf_response_is_rejected(self):
        for body in ('{"isSuccess":false}', '<html>login</html>', '[]',
                     '{"isSuccess":true}', '{"isSuccess":true,"result":null}',
                     '{"isSuccess":true,"result":{"headerName":"X-CSRF","token":""}}'):
            with self.subTest(body=body):
                self.env["MOCK_BODY"] = body
                result = self.run_smoke()
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("Traceback", result.stderr)

    def test_network_failure_is_rejected(self):
        self.env["MOCK_NETWORK_FAIL"] = "1"
        self.assertNotEqual(self.run_smoke().returncode, 0)

    def test_invalid_target_and_non_https_origin_are_rejected(self):
        self.env["SMOKE_TARGET_PORT"] = "9999"
        self.assertNotEqual(self.run_smoke().returncode, 0)
        self.env.pop("SMOKE_TARGET_PORT")
        self.env["SMOKE_BASE_URL"] = "http://smoke.invalid"
        self.assertNotEqual(self.run_smoke().returncode, 0)


if __name__ == "__main__":
    unittest.main()
