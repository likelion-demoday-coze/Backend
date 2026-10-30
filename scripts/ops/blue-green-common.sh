#!/usr/bin/env bash
load_config() {
  APP_DIR=${APP_DIR:-/home/ubuntu/Backend}
  source "$APP_DIR/deploy.env"
  STATE_DIR="$APP_DIR/deploy-state"
  UPSTREAM_FILE=${UPSTREAM_FILE:-/etc/nginx/backend/active-upstream.conf}
  : "${SMOKE_BASE_URL:?}" "${SMOKE_COOKIE_FILE:?}"
  export SMOKE_BASE_URL SMOKE_COOKIE_FILE
  test -r "$SMOKE_COOKIE_FILE"
  # Create only after the baseline has shared JDBC sessions and scheduler locks.
  test "$(cat "$STATE_DIR/blue-green-ready")" = v1
  test -f "$UPSTREAM_FILE"
  for cmd in docker curl python3 timeout flock nginx ps; do command -v "$cmd" >/dev/null; done
}
lock_deployment() {
  exec 9>"$STATE_DIR/deploy.lock"
  flock -n 9 || { echo 'Another deployment/rollback is running' >&2; return 1; }
}
other_slot() { case "$1" in blue) echo green;; green) echo blue;; *) return 1;; esac; }
port_for() { case "$1" in blue) echo 8080;; green) echo 8081;; *) return 1;; esac; }
container_for() { port_for "$1" >/dev/null && echo "backend-$1"; }
current_slot() {
  case "$(cat "$UPSTREAM_FILE")" in
    'server 127.0.0.1:8080;') echo blue;;
    'server 127.0.0.1:8081;') echo green;;
    *) echo 'Unknown nginx upstream; manual reconciliation required' >&2; return 1;;
  esac
}
verify_protocol() {
  test "$(docker inspect --format '{{index .Config.Labels "com.coze.deployment.protocol"}}' "$1")" = bluegreen-v1
}
outside_midnight() {
  local hhmm
  hhmm=$(TZ=Asia/Seoul date +%H%M)
  [[ $hhmm =~ ^[0-9]{4}$ ]] && ((10#$hhmm >= 20 && 10#$hhmm < 2350))
}
assert_outside_midnight() {
  outside_midnight || { echo 'Blocked 23:50–00:20 Asia/Seoul; serving slot retained' >&2; return 1; }
}
wait_healthy() {
  local container=$1 status running attempt
  for ((attempt=0; attempt<${HEALTH_ATTEMPTS:-60}; attempt++)); do
    running=$(docker inspect --format '{{.State.Running}}' "$container") || return 1
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container") || return 1
    [[ $running == true && $status == healthy ]] && return 0
    [[ $running == true && $status != unhealthy && $status != missing ]] || return 1
    sleep 5
  done
  return 1
}
verify_route() {
  local headers
  headers=$(curl --silent --show-error --fail --max-time 5 --dump-header - --output /dev/null \
    http://127.0.0.1:18080/api/v1/auth/csrf) || return 1
  printf '%s\n' "$headers" | tr -d '\r' | grep -Eiq "^X-Backend-Address: 127\\.0\\.0\\.1:$1$"
}
wait_route() {
  local i
  for ((i=0; i<${ROUTE_ATTEMPTS:-30}; i++)); do verify_route "$1" && return 0; sleep 1; done
  return 1
}
replace_upstream() {
  local temp
  temp=$(mktemp "$(dirname "$UPSTREAM_FILE")/.upstream.XXXXXX") || return 1
  cat "$1" > "$temp" || return 1
  chmod 644 "$temp" || return 1
  mv -f "$temp" "$UPSTREAM_FILE"
}
activate_route() {
  local temp
  temp=$(mktemp "$STATE_DIR/upstream.XXXXXX") || return 1
  printf 'server 127.0.0.1:%s;\n' "$1" > "$temp"
  replace_upstream "$temp" || return 1
  rm -f "$temp"
  nginx -t && nginx -s reload && wait_route "$1"
}
restore_route() {
  replace_upstream "$1" && nginx -t && nginx -s reload && wait_route "$2"
}
write_active() {
  printf '%s\n' "$1" > "$STATE_DIR/active-slot.tmp" && mv -f "$STATE_DIR/active-slot.tmp" "$STATE_DIR/active-slot"
}
snapshot_workers() {
  local master
  master=$(cat "${NGINX_PID_FILE:-/run/nginx.pid}") || return 1
  [[ $master =~ ^[0-9]+$ ]] || return 1
  ps -eo pid=,ppid=,args= | awk -v master="$master" -v filter="${1:-all}" \
    '$2 == master && /nginx: worker process/ && (filter == "all" || /is shutting down/) {print $1}'
}
wait_drained() {
  local elapsed pid busy
  for ((elapsed=0; elapsed<${DRAIN_SECONDS:-180}; elapsed+=2)); do
    busy=0
    while read -r pid; do
      [[ -z $pid ]] || { if kill -0 "$pid" 2>/dev/null; then busy=1; fi; }
    done < "$1"
    ((busy == 0)) && return 0
    sleep 2
  done
  return 1
}
