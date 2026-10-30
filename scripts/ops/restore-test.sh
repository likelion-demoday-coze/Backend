#!/usr/bin/env bash
set -euo pipefail
umask 077
source "${BACKUP_CONFIG:-/home/ubuntu/Backend/backup.env}"
: "${BACKUP_DIR:?}"
: "${MYSQL_DATABASE:?}"
[[ $MYSQL_DATABASE =~ ^[a-zA-Z0-9_]+$ ]] || exit 2
if [[ $# == 1 ]]; then
  FILE=$1
else
  shopt -s nullglob
  files=("$BACKUP_DIR"/mysql-*.sql.gz)
  ((${#files[@]})) || { echo 'No backup found' >&2; exit 1; }
  FILE=${files[${#files[@]}-1]}
fi
sha256sum --check "$FILE.sha256"
gzip -t "$FILE"
NAME="backend-restore-test-$$"
SECRET=$(mktemp)
cleanup() { docker rm -fv "$NAME" >/dev/null 2>&1 || true; rm -f -- "$SECRET"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')" > "$SECRET"
# Isolated disposable database: no host port, no production volume, events disabled.
# Pin RESTORE_MYSQL_IMAGE to the production MySQL version/digest in backup.env.
docker run -d --name "$NAME" --network none --env-file "$SECRET" \
  -v /var/lib/mysql "${RESTORE_MYSQL_IMAGE:-mysql:8.0}" --event-scheduler=OFF >/dev/null
ready=0
# The initialization server accepts socket connections but disables TCP.
# Wait for the final server before importing the backup.
for ((i=0; i<60; i++)); do
  if docker exec "$NAME" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uroot -Nse "SELECT 1"' >/dev/null 2>&1; then
    ready=1; break
  fi
  sleep 2
done
((ready == 1)) || { echo 'Restore database did not start' >&2; exit 1; }
gzip -dc "$FILE" | docker exec -i "$NAME" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
count=$(docker exec "$NAME" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "$1"' sh \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MYSQL_DATABASE' AND table_type='BASE TABLE'")
[[ $count =~ ^[0-9]+$ ]] && ((count > 0))
docker exec "$NAME" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqlcheck -uroot --check "$1"' sh "$MYSQL_DATABASE"
echo "Restore import and table checks passed: $FILE"
