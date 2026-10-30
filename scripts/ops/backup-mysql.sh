#!/usr/bin/env bash
set -euo pipefail
umask 077
# Use a dedicated MySQL option file, never put passwords in arguments or CI logs.
CONFIG=${BACKUP_CONFIG:-/home/ubuntu/Backend/backup.env}
source "$CONFIG"
: "${MYSQL_DEFAULTS_FILE:?}"
: "${MYSQL_DATABASE:?}"
: "${BACKUP_DIR:?}"
[[ $MYSQL_DATABASE =~ ^[a-zA-Z0-9_]+$ ]] || exit 2
mkdir -p "$BACKUP_DIR"
exec 8>"$BACKUP_DIR/backup.lock"
flock -w 60 8
FILE=$(mktemp "$BACKUP_DIR/mysql-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.sql.gz.partial")
trap 'rm -f -- "$FILE"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mysqldump --defaults-extra-file="$MYSQL_DEFAULTS_FILE" \
  --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
  --routines --events --triggers --hex-blob --databases "$MYSQL_DATABASE" | gzip > "$FILE"
gzip -t "$FILE"
mv -- "$FILE" "${FILE%.partial}"
FILE=${FILE%.partial}
trap - EXIT
sha256sum "$FILE" > "$FILE.sha256"
printf '%s\n' "$FILE"
