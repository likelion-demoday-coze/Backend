#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/blue-green-common.sh"
load_config
lock_deployment
ACTIVE=$(current_slot)
TARGET=$(other_slot "$ACTIVE")
PORT=$(port_for "$TARGET")
# The current app may be down during an incident. Its health must not prevent
# starting and validating the known-good target; the upstream file identifies it.
verify_protocol "$(container_for "$TARGET")"
test "$(cat "$STATE_DIR/known-good-$TARGET")" = "$(docker inspect --format '{{.Image}}' "$(container_for "$TARGET")")"
RECORD="$STATE_DIR/rollback-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir "$RECORD"
cp "$UPSTREAM_FILE" "$RECORD/previous-upstream"
docker start "$(container_for "$TARGET")"
wait_healthy "$(container_for "$TARGET")"
SMOKE_TARGET_PORT=$PORT bash "$SCRIPT_DIR/smoke.sh"
SWITCHED=0
finish() {
  local code=$?
  trap - EXIT
  if ((code != 0 && SWITCHED == 1)); then
    if restore_route "$RECORD/previous-upstream" "$(port_for "$ACTIVE")"; then
      write_active "$ACTIVE"
      echo restored-current > "$RECORD/status"
    else
      echo rollback-failed > "$RECORD/status"
    fi
  fi
  exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
SWITCHED=1
activate_route "$PORT"
bash "$SCRIPT_DIR/smoke.sh"
write_active "$TARGET"
echo success > "$RECORD/status"
echo "Rollback verified: $TARGET. Both slots retained for investigation."
