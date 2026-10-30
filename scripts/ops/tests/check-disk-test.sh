#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/bin"
cat > "$TMP/config" <<'CONFIG'
DB_DISK_PATH=/db
BACKUP_DIR=/backup
CONFIG
cat > "$TMP/bin/docker" <<'MOCK'
#!/usr/bin/env bash
echo /docker
MOCK
cat > "$TMP/bin/df" <<'MOCK'
#!/usr/bin/env bash
if [[ $1 == -PB1 ]]; then
  [[ ${FAIL_DF:-0} != 1 ]] || exit 1
  available=5368709120
  if [[ $2 == "${LOW_PATH:-}" ]]; then available=5368709119; fi
  printf 'Filesystem 1B-blocks Used Available Capacity Mounted\n'
  printf '/disk 10000000000 1000000000 %s 10%% /\n' "$available"
else
  printf 'Filesystem Blocks Used Available Capacity Mounted\n'
  printf '/disk 100 10 90 %s%% /\n' "${USED_PERCENT:-10}"
fi
MOCK
chmod +x "$TMP/bin/docker" "$TMP/bin/df"
export PATH="$TMP/bin:$PATH" DISK_CONFIG="$TMP/config"
unset MIN_FREE_BYTES MAX_DISK_PERCENT LOW_PATH FAIL_DF USED_PERCENT

expect_exit() {
  local expected=$1 actual=0
  shift
  "$@" > "$TMP/output" 2>&1 || actual=$?
  if [[ $actual != "$expected" ]]; then
    cat "$TMP/output" >&2
    echo "Expected exit $expected, got $actual" >&2
    exit 1
  fi
}

# Equality passes; one byte below the minimum fails on every checked path.
expect_exit 0 bash "$ROOT/check-disk.sh"
for path in / /docker /db /backup; do
  expect_exit 1 env LOW_PATH="$path" bash "$ROOT/check-disk.sh"
done
expect_exit 1 env FAIL_DF=1 bash "$ROOT/check-disk.sh"
expect_exit 1 env USED_PERCENT=85 bash "$ROOT/check-disk.sh"
expect_exit 0 env MIN_FREE_BYTES=1 bash "$ROOT/check-disk.sh"
for value in 0 -1 abc 01 9999999999999999999; do
  expect_exit 2 env MIN_FREE_BYTES="$value" bash "$ROOT/check-disk.sh"
done
echo 'Disk capacity checks passed'
