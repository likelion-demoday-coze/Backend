#!/usr/bin/env bash
set -euo pipefail
: "${SMOKE_BASE_URL:?}"
[[ $SMOKE_BASE_URL == https://* ]] || { echo 'Smoke tests require HTTPS' >&2; exit 1; }
base=${SMOKE_BASE_URL%/}
extra=()
port=${SMOKE_TARGET_PORT:-}
if [[ -n $port ]]; then
  case "$port" in
    8080) container=backend-blue ;;
    8081) container=backend-green ;;
    *) echo 'Invalid smoke target port' >&2; exit 2 ;;
  esac
  host=$(python3 -c 'import sys,urllib.parse; print(urllib.parse.urlsplit(sys.argv[1]).netloc)' "$base")
  extra=(--header "Host: $host" --header 'X-Forwarded-Proto: https')
  base="http://127.0.0.1:$port"
fi

# Responses can contain fresh CSRF tokens. Keep them private and out of logs.
umask 077
response=$(mktemp)
trap 'rm -f -- "$response"' EXIT
check_response() {
  local kind=$1
  shift
  if ! "$@" > "$response"; then
    echo "Smoke failed: $kind request failed" >&2
    return 1
  fi
  python3 - "$kind" "$response" <<'PY'
import json, sys
from pathlib import Path
kind, path = sys.argv[1:]
try:
    body, status = Path(path).read_text().rsplit("\n", 1)
    if status != "200":
        raise ValueError("HTTP " + status)
    data = json.loads(body)
    if not isinstance(data, dict):
        raise ValueError("expected JSON object")
    if kind == "readiness":
        valid = data.get("status") == "UP"
    else:
        result = data.get("result")
        valid = data.get("isSuccess") is True and isinstance(result, dict) and all(
            isinstance(result.get(key), str) and bool(result[key].strip())
            for key in ("headerName", "token")
        )
    if not valid:
        raise ValueError("unexpected response shape or unhealthy status")
except (ValueError, OSError) as error:
    # Do not print response bodies, tokens or JSON parser input.
    message = str(error) if type(error) is ValueError else "invalid response"
    sys.exit("Smoke failed: " + kind + " (" + message + ")")
PY
  echo "Smoke passed: $kind"
}

if [[ -n $port ]]; then
  # Readiness includes DB connectivity; management stays on container loopback.
  check_response readiness docker exec "$container" curl \
    --silent --show-error --connect-timeout 5 --max-time 20 \
    --write-out '\n%{http_code}' http://127.0.0.1:9090/actuator/health/readiness
fi
# No saved cookies or credentials. A fresh anonymous CSRF session may be created.
check_response csrf curl --silent --show-error --connect-timeout 5 --max-time 20 \
  "${extra[@]}" --write-out '\n%{http_code}' "$base/api/v1/auth/csrf"
