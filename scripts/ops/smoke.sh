#!/usr/bin/env bash
set -euo pipefail
: "${SMOKE_BASE_URL:?}"
: "${SMOKE_COOKIE_FILE:?}"
[[ $SMOKE_BASE_URL == https://* ]] || { echo 'Smoke tests require HTTPS' >&2; exit 1; }
extra=()
base=${SMOKE_BASE_URL%/}
if [[ -n ${SMOKE_TARGET_PORT:-} ]]; then
  [[ $SMOKE_TARGET_PORT == 8080 || $SMOKE_TARGET_PORT == 8081 ]] || exit 2
  # Select cookies against their real HTTPS origin, then send them only to the
  # loopback candidate. Secure cookies would otherwise be omitted on local HTTP.
  umask 077
  headers=$(mktemp)
  trap 'rm -f -- "$headers"' EXIT
  python3 - "$base" "$SMOKE_COOKIE_FILE" "$headers" <<'PY'
import http.cookiejar, sys, urllib.request, urllib.parse
base, cookie_file, output = sys.argv[1:]
jar = http.cookiejar.MozillaCookieJar(cookie_file)
jar.load(ignore_discard=True)
request = urllib.request.Request(base + '/api/v1/members/me')
jar.add_cookie_header(request)
cookie = request.get_header('Cookie')
if not cookie:
    raise SystemExit('No valid smoke session for the configured HTTPS origin')
with open(output, 'w') as file:
    file.write('Cookie: ' + cookie + '\nHost: ' + urllib.parse.urlsplit(base).netloc + '\nX-Forwarded-Proto: https\n')
PY
  extra=(--header "@$headers")
  base="http://127.0.0.1:$SMOKE_TARGET_PORT"
fi
# Do not follow redirects: a redirect to a login page is a failed smoke test.
for path in /api/v1/members/me /api/v1/stocks/me /api/v1/daily-quizzes/today /api/v1/products; do
  curl --silent --show-error --fail --connect-timeout 5 --max-time 20 \
    --cookie "$SMOKE_COOKIE_FILE" "${extra[@]}" --write-out '\n%{http_code}' "$base$path" |
    python3 -c 'import json,sys; body,status=sys.stdin.read().rsplit("\n",1); r=json.loads(body); sys.exit(0 if status == "200" and r.get("isSuccess") is True else 1)'
  echo "Smoke passed: $path"
done
