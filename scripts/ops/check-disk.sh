#!/usr/bin/env bash
set -euo pipefail
# All paths must exist. For a remote DB replace this hook with a provider metric check.
source "${DISK_CONFIG:-/home/ubuntu/Backend/disk.env}"
: "${DB_DISK_PATH:?Set the actual local DB data mount, or use a remote DB check hook}"
: "${BACKUP_DIR:?}"
MAX_DISK_PERCENT=${MAX_DISK_PERCENT:-85}
MIN_FREE_BYTES=${MIN_FREE_BYTES:-5368709120}
if ! [[ $MIN_FREE_BYTES =~ ^[1-9][0-9]{0,17}$ ]]; then
  echo 'MIN_FREE_BYTES must be a positive integer of at most 18 digits' >&2
  exit 2
fi
if ! [[ $MAX_DISK_PERCENT =~ ^[0-9]+$ ]] || ((MAX_DISK_PERCENT < 1 || MAX_DISK_PERCENT > 100)); then
  echo 'MAX_DISK_PERCENT must be an integer between 1 and 100' >&2
  exit 2
fi
docker_root=$(docker info --format '{{.DockerRootDir}}')
for path in / "$docker_root" "$DB_DISK_PATH" "$BACKUP_DIR"; do
  available=$(LC_ALL=C df -PB1 "$path" | awk 'NR==2 {print $4}')
  [[ $available =~ ^[0-9]{1,18}$ ]] && ((10#$available >= MIN_FREE_BYTES)) || {
    echo "Insufficient or unavailable free space: $path (available=${available:-unknown}, required=$MIN_FREE_BYTES bytes)" >&2
    exit 1
  }
  for flag in -P -Pi; do
    used=$(df "$flag" "$path" | awk 'NR==2 {gsub(/%/, "", $5); print $5}')
    [[ $used =~ ^[0-9]+$ ]] && ((used < MAX_DISK_PERCENT)) || {
      echo "Disk/inode threshold exceeded or unavailable: $path ($flag)" >&2
      exit 1
    }
  done
done
