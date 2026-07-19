#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/codedog}"
TDUCK_DIR=${TDUCK_DIR:-/opt/tduck-migration}
TDUCK_DEPLOY_DIR=${TDUCK_DEPLOY_DIR:-${TDUCK_DIR}/deploy}
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
set -a
source "$TDUCK_DEPLOY_DIR/.env"
set +a
docker compose -f "$TDUCK_DEPLOY_DIR/compose.yaml" exec -T -e MYSQL_PWD="$TDUCK_MYSQL_ROOT_PASSWORD" mysql mysqldump -u root --single-transaction --routines --triggers --default-character-set=utf8mb4 "$TDUCK_MYSQL_DATABASE" > "$STAGING/tduck-mysql.sql"
docker compose -f "$TDUCK_DEPLOY_DIR/compose.yaml" exec -T backend tar -czf - -C /application/upload . > "$STAGING/tduck-uploads.tar.gz"
cp "$TDUCK_DEPLOY_DIR/.env" "$STAGING/tduck.env"
cp "$PROJECT_DIR/.env" "$STAGING/.env"
tar -C "$PROJECT_DIR" --exclude=.git --exclude=.env --exclude=data --exclude=mysql-data --exclude=migration -czf "$STAGING/source.tar.gz" .
tar -C "$STAGING" -czf "$ARCHIVE" mysql.sql tduck-mysql.sql .env tduck.env tduck-uploads.tar.gz source.tar.gz
chmod 600 "$ARCHIVE"
find "$BACKUP_DIR" -type f -name 'codedog-*.tar.gz' -mtime +30 -delete
printf '%s\n' "$ARCHIVE"
