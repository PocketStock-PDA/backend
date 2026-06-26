#!/usr/bin/env bash
# reset-demo-rds.sh — 시연용 RDS를 로컬 fresh DB와 같은 상태로 재생성한다.
#
#   RESET_DEMO_RDS=YES ./deploy/scripts/reset-demo-rds.sh
#
# 주의: pocketstock_main / pocketstock_ledger 를 DROP DATABASE 후 다시 만든다.
# 운영 데이터 보존용 마이그레이션이 아니라, 시연 DB 재시드 전용 도구다.
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/app}"
COMPOSE_FILE="$APP_DIR/deploy/compose/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/deploy/compose/.env"
LOCK_FILE="/tmp/pocketstock-deploy.lock"

CORE_DB_NAME="pocketstock_main"
LEDGER_DB_NAME="pocketstock_ledger"
MYSQL_SSL_MODE="${MYSQL_SSL_MODE:-}"

log() { echo "[$(date -u +%H:%M:%S)] $*"; }
die() { echo "[ERROR] $*" >&2; exit 1; }

require_file() {
  [ -f "$1" ] || die "파일 없음: $1"
}

env_value() {
  local key="$1" line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
  [ -n "$line" ] || return 1
  printf '%s' "${line#*=}" | tr -d '\r'
}

require_env() {
  local key="$1" value
  value="$(env_value "$key" || true)"
  [ -n "$value" ] || die ".env 에 $key 값이 필요합니다."
  printf '%s' "$value"
}

mysql_args() {
  local host="$1" port="$2" user="$3"
  local args=(
    --protocol=TCP
    -h "$host"
    -P "$port"
    -u "$user"
    --default-character-set=utf8mb4
    --batch
    --show-warnings
  )
  [ -n "$MYSQL_SSL_MODE" ] && args+=(--ssl-mode="$MYSQL_SSL_MODE")
  printf '%s\0' "${args[@]}"
}

mysql_exec() {
  local host="$1" port="$2" user="$3" password="$4" sql="$5"
  local args=()
  while IFS= read -r -d '' arg; do args+=("$arg"); done < <(mysql_args "$host" "$port" "$user")
  MYSQL_PWD="$password" mysql "${args[@]}" -e "$sql"
}

mysql_query() {
  local host="$1" port="$2" user="$3" password="$4" sql="$5"
  local args=()
  while IFS= read -r -d '' arg; do args+=("$arg"); done < <(mysql_args "$host" "$port" "$user")
  MYSQL_PWD="$password" mysql "${args[@]}" --skip-column-names -e "$sql"
}

import_sql() {
  local label="$1" host="$2" port="$3" user="$4" password="$5" file="$6"
  local args=()
  require_file "$file"
  while IFS= read -r -d '' arg; do args+=("$arg"); done < <(mysql_args "$host" "$port" "$user")
  log "  import ${label}: ${file#$APP_DIR/}"

  # 일부 seed 파일은 UTF-8 BOM으로 시작한다. mysql client가 USE 앞 BOM을 토큰으로
  # 해석하지 않도록 첫 3바이트가 BOM일 때만 제거해서 흘려보낸다.
  if head -c 3 "$file" | LC_ALL=C grep -q $'^\xEF\xBB\xBF$'; then
    tail -c +4 "$file" | MYSQL_PWD="$password" mysql "${args[@]}"
  else
    MYSQL_PWD="$password" mysql "${args[@]}" < "$file"
  fi
}

count_table() {
  local label="$1" host="$2" port="$3" user="$4" password="$5" db="$6" table="$7"
  local count
  count="$(mysql_query "$host" "$port" "$user" "$password" "SELECT COUNT(*) FROM ${db}.${table};")"
  log "  ${label}.${table}=${count}"
}

[ "${RESET_DEMO_RDS:-}" = "YES" ] || die "파괴적 작업입니다. RESET_DEMO_RDS=YES 를 붙여 다시 실행하세요."
require_file "$COMPOSE_FILE"
require_file "$ENV_FILE"
command -v mysql >/dev/null 2>&1 || die "mysql CLI가 필요합니다."
command -v docker >/dev/null 2>&1 || die "docker CLI가 필요합니다."

CORE_HOST="$(require_env RDS_CORE_ENDPOINT)"
LEDGER_HOST="$(require_env RDS_LEDGER_ENDPOINT)"
CORE_USER="$(require_env CORE_DB_USERNAME)"
CORE_PASSWORD="$(require_env CORE_DB_PASSWORD)"
LEDGER_USER="$(require_env LEDGER_DB_USERNAME)"
LEDGER_PASSWORD="$(require_env LEDGER_DB_PASSWORD)"
CORE_PORT="$(env_value RDS_CORE_PORT || true)"; CORE_PORT="${CORE_PORT:-3306}"
LEDGER_PORT="$(env_value RDS_LEDGER_PORT || true)"; LEDGER_PORT="${LEDGER_PORT:-3306}"
MYSQL_SSL_MODE="$(env_value MYSQL_SSL_MODE || true)"; MYSQL_SSL_MODE="${MYSQL_SSL_MODE:-REQUIRED}"

CORE_SQL_FILES=(
  "$APP_DIR/scripts/mysql-a-init.sql"
  "$APP_DIR/scripts/stock-master/cards_seed.sql"
  "$APP_DIR/scripts/stock-master/card_benefits_seed.sql"
  "$APP_DIR/scripts/stock-master/dividend_stocks_seed.sql"
  "$APP_DIR/scripts/mysql-a-persona-seed.sql"
  "$APP_DIR/scripts/mysql-a-persona-seed-extra.sql"
  "$APP_DIR/scripts/mysql-a-persona-seed-suhyun.sql"
)

LEDGER_SQL_FILES=(
  "$APP_DIR/scripts/mysql-b-init.sql"
  "$APP_DIR/scripts/stock-master/tradable_stocks_seed.sql"
  "$APP_DIR/scripts/mysql-b-persona-seed.sql"
  "$APP_DIR/scripts/mysql-b-persona-seed-extra.sql"
)

log "=== 시연용 RDS reset/reseed 시작 ==="
log "대상 DB A: ${CORE_HOST}:${CORE_PORT}/${CORE_DB_NAME}"
log "대상 DB B: ${LEDGER_HOST}:${LEDGER_PORT}/${LEDGER_DB_NAME}"
log "mysql ssl-mode=${MYSQL_SSL_MODE}"

exec 9>"$LOCK_FILE"
flock -n 9 || die "다른 배포/DB 리셋이 진행 중입니다($LOCK_FILE)."

log "앱 컨테이너 정지(떠 있는 경우)"
docker stop \
  pocketstock-core-api-blue \
  pocketstock-core-api-green \
  pocketstock-ledger-api-blue \
  pocketstock-ledger-api-green \
  >/dev/null 2>&1 || log "  일부 앱 컨테이너가 없거나 이미 정지 상태입니다."

log "[DB A] drop/create: ${CORE_DB_NAME}"
mysql_exec "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" \
  "DROP DATABASE IF EXISTS ${CORE_DB_NAME}; CREATE DATABASE ${CORE_DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
for sql_file in "${CORE_SQL_FILES[@]}"; do
  import_sql "DB A" "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" "$sql_file"
done

log "[DB B] drop/create: ${LEDGER_DB_NAME}"
mysql_exec "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" \
  "DROP DATABASE IF EXISTS ${LEDGER_DB_NAME}; CREATE DATABASE ${LEDGER_DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
for sql_file in "${LEDGER_SQL_FILES[@]}"; do
  import_sql "DB B" "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" "$sql_file"
done

log "row count 검증"
count_table "DB A" "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" "$CORE_DB_NAME" users
count_table "DB A" "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" "$CORE_DB_NAME" cards
count_table "DB A" "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" "$CORE_DB_NAME" card_benefits
count_table "DB A" "$CORE_HOST" "$CORE_PORT" "$CORE_USER" "$CORE_PASSWORD" "$CORE_DB_NAME" card_transactions
count_table "DB B" "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" "$LEDGER_DB_NAME" tradable_stocks
count_table "DB B" "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" "$LEDGER_DB_NAME" cma_accounts
count_table "DB B" "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" "$LEDGER_DB_NAME" cma_transactions
count_table "DB B" "$LEDGER_HOST" "$LEDGER_PORT" "$LEDGER_USER" "$LEDGER_PASSWORD" "$LEDGER_DB_NAME" securities_accounts

log "=== 시연용 RDS reset/reseed 완료 ==="
log "앱 컨테이너는 정지 상태입니다. 필요하면 deploy/scripts/deploy-release.sh <40자리 SHA> 로 재배포하세요."
