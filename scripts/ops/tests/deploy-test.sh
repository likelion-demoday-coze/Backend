#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
for script in "$ROOT"/*.sh "$ROOT"/tests/*.sh; do bash -n "$script"; done
TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/bin"
cat > "$TMP/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -eu
echo "docker $*" >> "$APP_DIR/commands"
case "$1" in
  pull) [[ ${FAIL:-} != pull ]] ;;
  container) [[ ${INACTIVE_EXISTS:-0} == 1 ]] ;;
  inspect)
    case "$3" in
      *com.coze.deployment.protocol*) echo bluegreen-v1 ;;
      '{{.State.Running}}') echo true ;;
      *State.Health*)
        if [[ ${FAIL:-} == health && $4 == backend-green ]] || [[ ${FAIL:-} == rollback-health && $4 == backend-blue ]]; then echo unhealthy; else echo healthy; fi ;;
      *) echo 'sha256:immutable registry/backend:previous-sha' ;;
    esac ;;
  run) [[ ${FAIL:-} != run ]] || exit 1; touch "$APP_DIR/created"; echo new ;;
  stop) [[ ${FAIL:-} != stop ]] ;;
  start|rm) exit 0 ;;
  *) echo "Unexpected docker call: $*" >&2; exit 1 ;;
esac
MOCK
cat > "$TMP/bin/nginx" <<'MOCK'
#!/usr/bin/env bash
set -eu
echo "nginx $*" >> "$APP_DIR/commands"
port=$(sed -n 's/server 127.0.0.1:\([0-9]*\);/\1/p' "$APP_DIR/upstream")
if [[ $port == 8081 ]]; then
  [[ ${FAIL:-} != nginx ]] || exit 1
  [[ $1 != -s || ${FAIL:-} != reload ]] || exit 1
fi
if [[ $1 == -s ]]; then echo "$port" > "$APP_DIR/live-port"; fi
MOCK
cat > "$TMP/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -eu
echo "curl $*" >> "$APP_DIR/commands"
if [[ $* == *18080* ]]; then
  port=$(cat "$APP_DIR/live-port")
  if [[ ${CURRENT_DOWN:-0} == 1 && $port == 8081 ]]; then exit 22; fi
  if [[ ${FAIL:-} == route && $port == 8081 ]]; then port=8080; fi
  if [[ ${FAIL:-} == rollback-route && $port == 8080 && -e $APP_DIR/telemetry-called ]]; then exit 22; fi
  printf 'HTTP/1.1 200 OK\r\nX-Backend-Address: 127.0.0.1:%s\r\n' "$port"
else
  [[ ${FAIL:-} != candidate-smoke ]] || exit 22
  if [[ ${FAIL:-} == public-smoke && $* == *https://* ]]; then exit 22; fi
  printf '{"isSuccess":true}\n200'
fi
MOCK
cat > "$TMP/bin/python3" <<'MOCK'
#!/usr/bin/env bash
cat >/dev/null
if [[ $1 == - ]]; then echo 'Cookie: mock' > "$4"; fi
MOCK
cat > "$TMP/bin/date" <<'MOCK'
#!/usr/bin/env bash
if [[ $1 == +%H%M ]]; then
  if [[ ${FAIL:-} == cross-midnight && -e $APP_DIR/created ]]; then echo 0005
  elif [[ ${FAIL:-} == midnight-after-switch && $(cat "$APP_DIR/live-port") == 8081 ]]; then echo 0005
  else echo "${CLOCK:-1200}"; fi
else /bin/date "$@"; fi
MOCK
cat > "$TMP/bin/flock" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
cat > "$TMP/bin/timeout" <<'MOCK'
#!/usr/bin/env bash
shift
exec "$@"
MOCK
cat > "$TMP/bin/sleep" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
cat > "$TMP/bin/ps" <<'MOCK'
#!/usr/bin/env bash
if [[ ${FAIL:-} == drain ]]; then printf '%s 123 nginx: worker process\n' "$TEST_PID"; fi
if [[ ${FAIL:-} == prior-drain ]]; then printf '%s 123 nginx: worker process is shutting down\n' "$TEST_PID"; fi
exit 0
MOCK
cat > "$TMP/hook" <<'MOCK'
#!/usr/bin/env bash
set -eu
name=$(basename "$0")
echo "$name" >> "$APP_DIR/commands"
[[ ${FAIL:-} != "$name" ]] || exit 1
if [[ $name == telemetry ]]; then
  touch "$APP_DIR/telemetry-called"
  [[ ${FAIL:-} != rollback-route ]] || exit 1
fi
[[ $name != backup ]] || echo '/private/backup.sql.gz'
MOCK
chmod +x "$TMP/bin/"* "$TMP/hook"
for name in backup precheck telemetry; do cp "$TMP/hook" "$TMP/$name"; done
export PATH="$TMP/bin:$PATH" TEST_PID=$$
export HEALTH_ATTEMPTS=1 ROUTE_ATTEMPTS=2 SOAK_SECONDS=0 DRAIN_SECONDS=2
SHA=0123456789012345678901234567890123456789
run_case() {
  local name=$1 expected=$2 fail=$3 code=0
  export APP_DIR="$TMP/$name" FAIL="$fail" CLOCK=1200 INACTIVE_EXISTS=0
  [[ $fail != midnight ]] || export CLOCK=0005
  mkdir -p "$APP_DIR/deploy-state"
  echo v1 > "$APP_DIR/deploy-state/blue-green-ready"
  echo blue > "$APP_DIR/deploy-state/active-slot"
  echo 'server 127.0.0.1:8080;' > "$APP_DIR/upstream"
  echo 8080 > "$APP_DIR/live-port"
  echo 123 > "$APP_DIR/nginx.pid"
  touch "$APP_DIR/cookies"
  touch "$APP_DIR/commands"
  echo 'sha256:immutable registry/backend:previous-sha' > "$APP_DIR/deploy-state/known-good-blue"
  cat > "$APP_DIR/deploy.env" <<CONFIG
BACKUP_SCRIPT='$TMP/backup'
PRE_DEPLOY_CHECK_SCRIPT='$TMP/precheck'
POST_DEPLOY_CHECK_SCRIPT='$TMP/telemetry'
SMOKE_BASE_URL=https://smoke.invalid
SMOKE_COOKIE_FILE='$APP_DIR/cookies'
UPSTREAM_FILE='$APP_DIR/upstream'
NGINX_PID_FILE='$APP_DIR/nginx.pid'
CONFIG
  bash "$ROOT/deploy.sh" "$SHA" > "$APP_DIR/output" 2>&1 || code=$?
  local records=("$APP_DIR"/deploy-state/*-"$SHA"-*)
  if [[ $expected == success* ]]; then
    if [[ $code != 0 ]]; then cat "$APP_DIR/output" >&2; return 1; fi
    [[ $(cat "${records[0]}/status") == "$expected" ]]
    [[ $(cat "$APP_DIR/deploy-state/active-slot") == green ]]
    if [[ $expected == success ]]; then
      grep -q '^docker stop --time 1900 backend-blue$' "$APP_DIR/commands"
      # Serving Blue is stopped strictly AFTER the successful nginx reload.
      awk '/nginx -s reload/ {switched=1} /docker stop --time 1900 backend-blue/ {if (!switched) exit 1}' "$APP_DIR/commands"
    else ! grep -q '^docker stop .*backend-blue$' "$APP_DIR/commands"; fi
  else
    [[ $code != 0 ]]
    [[ $(cat "$APP_DIR/deploy-state/active-slot") == blue ]]
    ! grep -q '^docker stop .*backend-blue$' "$APP_DIR/commands"
    if [[ $expected != blocked ]]; then [[ $(cat "${records[0]}/status") == "$expected" ]]; fi
    if [[ $expected != rollback-failed ]]; then [[ $(cat "$APP_DIR/live-port") == 8080 ]]; fi
  fi
  echo "PASS $name"
}
run_case normal success ''
run_case backup-failure preflight backup
run_case capacity-failure preflight precheck
run_case pull-failure preflight pull
run_case previous-requests-not-finished preflight prior-drain
run_case start-failure failed-before-switch run
run_case readiness-failure failed-before-switch health
run_case candidate-smoke-failure failed-before-switch candidate-smoke
run_case nginx-test-failure rolled-back nginx
run_case nginx-reload-failure rolled-back reload
run_case route-verification-failure rolled-back route
run_case public-smoke-failure rolled-back public-smoke
run_case telemetry-failure rolled-back telemetry
run_case rollback-failure rollback-failed rollback-route
run_case protected-midnight blocked midnight
run_case protection-before-cutover failed-before-switch cross-midnight
run_case protection-after-cutover success-old-retained midnight-after-switch
run_case drain-timeout success-old-retained drain
run_case old-stop-failure success-old-retained stop
# A stopped previous slot is started and checked while Green keeps serving.
export APP_DIR="$TMP/normal" FAIL='' CURRENT_DOWN=1
bash "$ROOT/rollback.sh" > "$APP_DIR/rollback-output" 2>&1
[[ $(cat "$APP_DIR/deploy-state/active-slot") == blue ]]
grep -q '^docker start backend-blue$' "$APP_DIR/commands"
echo 'PASS manual-rollback-with-current-server-down'
export CURRENT_DOWN=0 FAIL=health
if bash "$ROOT/rollback.sh" > "$APP_DIR/rollback-failure-output" 2>&1; then exit 1; fi
[[ $(cat "$APP_DIR/live-port") == 8080 ]]
echo 'PASS manual-rollback-rejects-unhealthy-target'
export APP_DIR="$TMP/telemetry-failure" FAIL=''
if bash "$ROOT/rollback.sh" > "$APP_DIR/rollback-failure-output" 2>&1; then exit 1; fi
[[ $(cat "$APP_DIR/live-port") == 8080 ]]
echo 'PASS manual-rollback-rejects-unverified-candidate'
# All protected-window boundaries use KST, regardless of the host timezone.
source "$ROOT/blue-green-common.sh"
for CLOCK in 0000 0019 2350 2359; do export CLOCK; if outside_midnight; then exit 1; fi; done
for CLOCK in 0020 1200 2349; do export CLOCK; outside_midnight; done
echo 'PASS midnight-boundaries'
