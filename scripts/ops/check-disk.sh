#!/usr/bin/env bash
set -euo pipefail
# All paths must exist. For a remote DB replace this hook with a provider metric check.
source "${DISK_CONFIG:-/home/ubuntu/Backend/disk.env}"
: "${DB_DISK_PATH:?Set the actual local DB data mount, or use a remote DB check hook}"
: "${BACKUP_DIR:?}"
MAX_DISK_PERCENT=${MAX_DISK_PERCENT:-85}
if ! [[ $MAX_DISK_PERCENT =~ ^[0-9]+$ ]] || ((MAX_DISK_PERCENT < 1 || MAX_DISK_PERCENT > 100)); then
  echo 'MAX_DISK_PERCENT must be an integer between 1 and 100' >&2
  exit 2
fi
docker_root=$(docker info --format '{{.DockerRootDir}}')
for path in / "$docker_root" "$DB_DISK_PATH" "$BACKUP_DIR"; do
  for flag in -P -Pi; do
    used=$(df "$flag" "$path" | awk 'NR==2 {gsub(/%/, "", $5); print $5}')
    [[ $used =~ ^[0-9]+$ ]] && ((used < MAX_DISK_PERCENT)) || {
      echo "Disk/inode threshold exceeded or unavailable: $path ($flag)" >&2
      exit 1
    }
  done
done
