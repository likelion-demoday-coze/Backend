#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SHA=${1:?Usage: deploy.sh COMMIT_SHA}
[[ $SHA =~ ^[0-9a-f]{40}$ ]] || exit 2
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/blue-green-common.sh"
load_config
lock_deployment
assert_outside_midnight
: "${BACKUP_SCRIPT:?}" "${PRE_DEPLOY_CHECK_SCRIPT:?}" "${POST_DEPLOY_CHECK_SCRIPT:?}"
for hook in "$BACKUP_SCRIPT" "$PRE_DEPLOY_CHECK_SCRIPT" "$POST_DEPLOY_CHECK_SCRIPT"; do test -x "$hook"; done
ACTIVE=$(current_slot)
OLD=$(container_for "$ACTIVE")
NEXT=$(other_slot "$ACTIVE")
NEW=$(container_for "$NEXT")
PORT=$(port_for "$NEXT")
verify_route "$(port_for "$ACTIVE")"
docker inspect --format '{{.Id}}' "$OLD" >/dev/null
verify_protocol "$OLD"
IMAGE="ghcr.io/likelion-demoday-coze/backend:$SHA"
RECORD="$STATE_DIR/$(date -u +%Y%m%dT%H%M%SZ)-$SHA-$$"
mkdir "$RECORD"
export DEPLOY_SHA=$SHA DEPLOY_RECORD_DIR=$RECORD
printf '%s\n' "$IMAGE" > "$RECORD/requested-image"
printf '%s\n' "$ACTIVE" > "$RECORD/previous-slot"
docker inspect --format '{{.Image}} {{.Config.Image}}' "$OLD" > "$RECORD/previous-image"
cp "$UPSTREAM_FILE" "$RECORD/previous-upstream"
echo preflight > "$RECORD/status"
# A preceding rollback may still have workers serving the inactive slot.
snapshot_workers draining > "$RECORD/prior-workers"
wait_drained "$RECORD/prior-workers" || { echo 'Prior nginx workers are still draining; retry later' >&2; exit 1; }
timeout 120 "$PRE_DEPLOY_CHECK_SCRIPT"
docker pull "$IMAGE"
timeout 900 "$BACKUP_SCRIPT" > "$RECORD/backup-reference"
test -s "$RECORD/backup-reference"
assert_outside_midnight
SWITCHED=0
COMMITTED=0
finish() {
  local code=$?
  trap - EXIT
  if ((code != 0 && COMMITTED == 0)); then
    set +e
    if ((SWITCHED == 1)); then
      # Old nginx workers may still use the candidate: retain both containers.
      if restore_route "$RECORD/previous-upstream" "$(port_for "$ACTIVE")"; then
        write_active "$ACTIVE"
        echo rolled-back > "$RECORD/status"
      else
        echo rollback-failed > "$RECORD/status"
        echo 'ROLLBACK FAILED: both containers retained; inspect nginx' >&2
      fi
    else
      echo failed-before-switch > "$RECORD/status"
    fi
  elif ((code != 0)); then
    echo success-old-retained > "$RECORD/status"
  fi
  exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Reclaim only the inactive slot; serving slot continues throughout startup.
if docker container inspect "$NEW" >/dev/null 2>&1; then
  docker stop --time 1900 "$NEW"
  docker rm "$NEW"
fi
rm -f "$STATE_DIR/known-good-$NEXT"
docker run -d --name "$NEW" --restart unless-stopped \
  --label "deployment.sha=$SHA" \
  -e "DEPLOY_SHA=$SHA" \
  -p "127.0.0.1:$PORT:8080" --env-file "$APP_DIR/.env" \
  -e JPA_DDL_AUTO=validate -e SPRING_SESSION_JDBC_INITIALIZE_SCHEMA=never \
  -e MANAGEMENT_SERVER_PORT=9090 -e MANAGEMENT_SERVER_ADDRESS=127.0.0.1 \
  "$IMAGE" > "$RECORD/new-container"
wait_healthy "$NEW"
SMOKE_TARGET_PORT=$PORT bash "$SCRIPT_DIR/smoke.sh"
assert_outside_midnight
snapshot_workers > "$RECORD/old-workers"
SWITCHED=1
activate_route "$PORT"
bash "$SCRIPT_DIR/smoke.sh"
timeout 180 "$POST_DEPLOY_CHECK_SCRIPT"
for ((i=0; i<${SOAK_SECONDS:-60}; i+=5)); do
  sleep 5
  wait_healthy "$NEW"
  verify_route "$PORT"
done
docker inspect --format '{{.Image}} {{.Config.Image}}' "$NEW" > "$RECORD/current-image"
write_active "$NEXT"
docker inspect --format '{{.Image}}' "$NEW" > "$STATE_DIR/known-good-$NEXT"
echo success > "$RECORD/status"
# Cleanup failure must never reroute traffic to a stopping container.
SWITCHED=0
COMMITTED=1
if wait_drained "$RECORD/old-workers" && outside_midnight; then
  if ! docker stop --time 1900 "$OLD"; then
    echo success-old-retained > "$RECORD/status"
    echo 'New slot serves traffic; old slot stop needs operator review' >&2
  fi
else
  echo success-old-retained > "$RECORD/status"
  echo 'Old slot retained: midnight protection or nginx requests still draining'
fi
echo "Deployment verified: $NEXT; record: $RECORD"
