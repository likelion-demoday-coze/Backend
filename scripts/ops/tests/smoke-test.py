"""Test the real smoke script with real cookie selection and JSON parsing."""
import http.cookiejar
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
        curl = binary / "curl"
        curl.write_text('''#!/usr/bin/env bash
echo "$*" >> "$MOCK_LOG"
for value in "$@"; do
  case "$value" in @*) cat "${value#@}" >> "$MOCK_LOG";; esac
done
[[ ${MOCK_NETWORK_FAIL:-0} == 0 ]] || exit 22
printf '%s\\n%s' "$MOCK_BODY" "$MOCK_STATUS"
''', encoding="utf-8", newline="\n")
        python = binary / "python3"
        python.write_text("#!/usr/bin/env bash\nexec " + shlex.quote(Path(sys.executable).as_posix()) + ' "$@"\n', encoding="utf-8", newline="\n")
        curl.chmod(0o755)
        python.chmod(0o755)
        cookies = folder / "cookies"
        jar = http.cookiejar.MozillaCookieJar(str(cookies))
        jar.set_cookie(http.cookiejar.Cookie(
            0, "SESSION", "test-session-only", None, False, "smoke.invalid", False,
            False, "/", True, True, None, True, None, None, {}, False))
        jar.save(ignore_discard=True)
        self.log = folder / "requests"
        self.env = dict(os.environ,
                        SMOKE_BASE_URL="https://smoke.invalid", SMOKE_COOKIE_FILE=cookies.as_posix(),
                        MOCK_BIN=binary.as_posix(), MOCK_LOG=self.log.as_posix(), MOCK_BODY='{"isSuccess":true}', MOCK_STATUS="200")
        self.env.pop("SMOKE_TARGET_PORT", None)

    def run_smoke(self):
        command = '''mock_bin=$MOCK_BIN
if command -v cygpath >/dev/null; then mock_bin=$(cygpath -u "$mock_bin"); fi
export PATH="$mock_bin:$PATH"
[[ $(command -v curl) == "$mock_bin/curl" ]] || exit 2
exec bash scripts/ops/smoke.sh'''
        return subprocess.run([os.environ.get("BASH_EXECUTABLE", "bash"), "-c", command],
                              cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)

    def test_public_success_checks_four_routes(self):
        result = self.run_smoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.count("Smoke passed:"), 4)

    def test_local_candidate_receives_secure_session_without_logging_it(self):
        self.env["SMOKE_TARGET_PORT"] = "8081"
        result = self.run_smoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        requests = self.log.read_text()
        self.assertIn("http://127.0.0.1:8081/api/v1/members/me", requests)
        self.assertIn("Cookie: SESSION=test-session-only", requests)
        self.assertIn("Host: smoke.invalid", requests)
        self.assertNotIn("test-session-only", result.stdout + result.stderr)

    def test_redirect_is_rejected_even_with_success_json(self):
        self.env["MOCK_STATUS"] = "302"
        self.assertNotEqual(self.run_smoke().returncode, 0)

    def test_failure_json_is_rejected(self):
        self.env["MOCK_BODY"] = '{"isSuccess":false}'
        self.assertNotEqual(self.run_smoke().returncode, 0)

    def test_html_login_page_is_rejected(self):
        self.env["MOCK_BODY"] = "<html>login</html>"
        self.assertNotEqual(self.run_smoke().returncode, 0)

    def test_network_failure_is_rejected(self):
        self.env["MOCK_NETWORK_FAIL"] = "1"
        self.assertNotEqual(self.run_smoke().returncode, 0)

    def test_missing_session_blocks_candidate_check(self):
        Path(self.env["SMOKE_COOKIE_FILE"]).write_text("# Netscape HTTP Cookie File\n")
        self.env["SMOKE_TARGET_PORT"] = "8081"
        self.assertNotEqual(self.run_smoke().returncode, 0)


if __name__ == "__main__":
    unittest.main()
