#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

DB_NAME="${INTELLICONF_DB_NAME:-intelliconf}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/intelliconf/backups/database}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
LOCK_FILE="${LOCK_FILE:-/run/lock/intelliconf-db-backup.lock}"

command -v mariadb >/dev/null
command -v mariadb-admin >/dev/null
command -v mariadb-dump >/dev/null
command -v gzip >/dev/null
command -v sha256sum >/dev/null
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]

mkdir -p "$BACKUP_ROOT" "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "another IntelliConference database backup is already running" >&2
  exit 75
fi

mariadb-admin ping --silent >/dev/null

stamp="$(date +%Y%m%d-%H%M%S)"
day="${stamp:0:8}"
destination="$BACKUP_ROOT/$day"
dump="$destination/${DB_NAME}-${stamp}.sql.gz"
temporary="$destination/.${DB_NAME}-${stamp}.sql.gz.tmp"
checksum="$dump.sha256"
manifest="$destination/${DB_NAME}-${stamp}.manifest"

mkdir -p "$destination"
trap 'rm -f "$temporary"' EXIT

mariadb-dump \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  --routines \
  --events \
  --triggers \
  --hex-blob \
  --default-character-set=utf8mb4 \
  --no-tablespaces \
  "$DB_NAME" | gzip -9 >"$temporary"

test -s "$temporary"
gzip -t "$temporary"
mv "$temporary" "$dump"
(
  cd "$destination"
  sha256sum "$(basename "$dump")" >"$(basename "$checksum")"
)

table_count="$(mariadb -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
database_bytes="$(mariadb -N -B -e "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
cat >"$manifest" <<EOF
created_at=$(date -Is)
database=$DB_NAME
table_count=$table_count
database_bytes=$database_bytes
dump_file=$(basename "$dump")
dump_bytes=$(stat -c %s "$dump")
dump_sha256=$(sha256sum "$dump" | awk '{print $1}')
retention_days=$RETENTION_DAYS
EOF

ln -sfn "$day" "$BACKUP_ROOT/latest"
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -exec rm -rf -- {} +

echo "backup_ok dump=$dump tables=$table_count bytes=$(stat -c %s "$dump")"
