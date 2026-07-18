#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/codedog}"
BACKUP_DIR="${BACKUP_DIR:-/opt/codedog-backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
STAGING="$(mktemp -d)"
ARCHIVE="${BACKUP_DIR}/codedog-${STAMP}.tar.gz"

cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT

set -a
source "$PROJECT_DIR/.env"
set +a
install -d -m 700 "$BACKUP_DIR"
docker compose -f "$PROJECT_DIR/compose.yaml" exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysqldump -u root --single-transaction --routines --triggers --default-character-set=utf8mb4 "$MYSQL_DATABASE" \
  > "$STAGING/mysql.sql"
cp "$PROJECT_DIR/.env" "$STAGING/.env"
tar -C "$PROJECT_DIR" --exclude=.git --exclude=.env --exclude=data --exclude=mysql-data --exclude=migration -czf "$STAGING/source.tar.gz" .
tar -C "$STAGING" -czf "$ARCHIVE" mysql.sql .env source.tar.gz
chmod 600 "$ARCHIVE"
find "$BACKUP_DIR" -type f -name 'codedog-*.tar.gz' -mtime +30 -delete
printf '%s\n' "$ARCHIVE"
