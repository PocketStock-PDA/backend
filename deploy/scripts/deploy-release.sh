#!/usr/bin/env bash
# deploy-release.sh — EC2에서 실행하는 무중단(Blue-Green) 릴리스 배포 스크립트 (CICD가 SSH로 호출).
#
#   ./deploy-release.sh <40자리-commit-sha>
#
# 동작(steady-state): ECR pull → 반대 색(blue↔green)을 새 이미지로 기동 → health 게이트 →
#   nginx upstream 을 새 색으로 reload 스위치(REST 0초) → 옛 색 정리 → ledger 단일활성 인계.
#   메모리 절약 위해 core 를 먼저 완전 교체한 뒤 ledger 를 교체한다(피크 앱 JVM 3개).
# 빌드는 하지 않는다(이미지는 CI에서 ECR로 push됨).
#
# ── ledger 단일활성 핸드오버 ──────────────────────────────────────────────
#   ledger 백그라운드(LS/KIS 실시간·매칭·스케줄러)는 단일 인스턴스 전제(LedgerActivation 게이트).
#   순서: 스위치 → 옛 색 quiesce(틱 드레인) → 옛 색 정지 → Redis active-color 전환 → 새 색 rearm.
#   quiesce→정지가 rearm 보다 먼저라 "동시 1개만 활성" + LS/KIS 세션 비중첩이 보장된다.
set -euo pipefail

# ---- 경로/상수 (EC2 배치 기준: 레포가 ~/app 에 git clone 되어 있음) ----
APP_DIR="${APP_DIR:-$HOME/app}"
COMPOSE_FILE="$APP_DIR/deploy/compose/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/deploy/compose/.env"
STATE_FILE="$APP_DIR/deploy/compose/.released_sha"     # 직전 성공 SHA(참고/롤백 재배포용)
COLOR_FILE="$APP_DIR/deploy/compose/.active_color"     # 현재 활성 색(blue/green)
UPSTREAM_FILE="$APP_DIR/deploy/nginx/conf.d/active-upstream.conf"  # nginx 활성 색 upstream(untracked)
LOCK_FILE="/tmp/pocketstock-deploy.lock"
PROJECT="pocketstock"
NGINX_CTN="pocketstock-nginx"
REDIS_CTN="pocketstock-redis"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-150}"   # 서비스당 health 대기 한도(초)
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"      # 스위치 후 옛 색 in-flight(요청·틱) 드레인 대기

log() { echo "[$(date -u +%H:%M:%S)] $*"; }
die() { echo "[ERROR] $*" >&2; exit 1; }

# ---- 인자 검증: GitHub Actions가 임의 문자열을 못 넣게 SHA 형식만 허용 ----
NEW_SHA="${1:-}"
[[ "$NEW_SHA" =~ ^[0-9a-f]{40}$ ]] || die "릴리스 SHA(40 hex)가 필요합니다. 받은 값: '${NEW_SHA}'"
[ -f "$COMPOSE_FILE" ] || die "compose 파일 없음: $COMPOSE_FILE"
[ -f "$ENV_FILE" ]     || die ".env 없음: $ENV_FILE (deploy/compose/.env.example 참고해 EC2에 생성)"

# ---- 동시 배포 방지 ----
exec 9>"$LOCK_FILE"
flock -n 9 || die "다른 배포가 진행 중입니다($LOCK_FILE)."

# ---- ECR_REGISTRY 는 .env 에서 읽음 ----
ECR_REGISTRY="$(grep -E '^ECR_REGISTRY=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
[ -n "$ECR_REGISTRY" ] || die ".env 에 ECR_REGISTRY 가 비어 있습니다."
export ECR_REGISTRY
export IMAGE_TAG="$NEW_SHA"

dc() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

# 한 서비스가 healthy 될 때까지 대기 (앱 포트는 호스트 미노출이라 컨테이너 healthcheck 로 판정)
wait_healthy() {
  local svc="$1" timeout="$HEALTH_TIMEOUT" elapsed=0 cid status
  cid="$(dc ps -q "$svc")"
  [ -n "$cid" ] || { log "  $svc 컨테이너를 찾지 못함"; return 1; }
  while true; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo gone)"
    case "$status" in
      healthy) log "  $svc → healthy"; return 0 ;;
      unhealthy) log "  $svc → unhealthy"; return 1 ;;
      gone) log "  $svc 컨테이너 사라짐"; return 1 ;;
    esac
    [ "$elapsed" -ge "$timeout" ] && { log "  $svc health 대기 시간 초과(${timeout}s, 마지막=$status)"; return 1; }
    sleep 3; elapsed=$((elapsed + 3))
  done
}

# active-upstream.conf 를 지정 색으로 (재)생성 — nginx 가 *.conf 글롭으로 자동 로드.
write_upstream() {
  local core_color="$1" ledger_color="$2"
  cat > "$UPSTREAM_FILE" <<EOF
# 무중단 배포 활성 색 upstream — deploy-release.sh 생성(자동), 직접 수정 금지.
upstream core-api   { server core-api-${core_color}:8081; }
upstream ledger-api { server ledger-api-${ledger_color}:8082; }
EOF
  log "active-upstream.conf → core=${core_color} ledger=${ledger_color}"
}

# nginx 설정 검증 후 graceful reload (실패 시 reload 안 함 → 옛 설정 유지)
reload_nginx() {
  docker exec "$NGINX_CTN" nginx -t || { log "nginx 설정 검증 실패 — reload 취소"; return 1; }
  docker exec "$NGINX_CTN" nginx -s reload
  log "nginx reload 완료(graceful)"
}

opposite() { [ "$1" = "blue" ] && echo "green" || echo "blue"; }

ensure_redis() {
  log "redis 기동(필요 시)"
  dc up -d redis
  wait_healthy redis || die "redis 가 healthy 되지 않음"
}

ensure_kafka() {
  log "kafka 기동(필요 시, 알림 이벤트 브로커)"
  dc up -d kafka \
    || { log "  kafka 기동 실패 — API 배포는 계속하되 알림은 outbox에 적재 후 Kafka 복구 시 발행"; return 0; }
  wait_healthy kafka \
    || log "  kafka 가 healthy 되지 않음 — API 배포는 계속하되 알림은 outbox에 적재 후 Kafka 복구 시 발행"
}

ensure_infra() {
  ensure_redis
  ensure_kafka
}

ensure_existing_active_service() {
  local svc="$1" cid state
  cid="$(dc ps -a -q "$svc")"
  [ -n "$cid" ] || die "활성 서비스 컨테이너 없음: $svc"

  state="$(docker inspect -f '{{.State.Status}}' "$cid")" \
    || die "활성 서비스 상태 확인 실패: $svc"
  case "$state" in
    running|restarting)
      log "  $svc: 실행 중(${state})"
      ;;
    exited|created)
      log "  $svc: ${state} → 기존 컨테이너 재시작"
      dc start "$svc" >/dev/null || die "활성 서비스 재시작 실패: $svc"
      ;;
    paused)
      log "  $svc: paused → unpause"
      docker unpause "$cid" >/dev/null || die "활성 서비스 unpause 실패: $svc"
      ;;
    *)
      die "활성 서비스 상태가 배포를 진행할 수 없음: $svc 상태=${state}"
      ;;
  esac
  wait_healthy "$svc" || die "활성 서비스 health 실패: $svc"
}

ensure_active_stack() {
  local active_color="$1"
  log "활성 색 컨테이너 확인/복구: ${active_color}"
  # reset-demo-rds.sh 이후에는 앱 컨테이너가 stopped 상태일 수 있다.
  # 기존 컨테이너를 start 해야 직전 SHA를 유지한 채 nginx upstream 검증이 가능하다.
  ensure_existing_active_service "core-api-${active_color}"
  ensure_existing_active_service "ledger-api-${active_color}"
}

log "=== 배포 시작 : $NEW_SHA ==="
log "ECR 로그인: $ECR_REGISTRY"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"
IMAGE_TAG="$NEW_SHA" dc config -q || die "compose 설정 검증 실패"
dc pull core-api-blue core-api-green ledger-api-blue ledger-api-green 2>/dev/null || dc pull

PREV_SHA=""; [ -f "$STATE_FILE" ] && PREV_SHA="$(cat "$STATE_FILE" 2>/dev/null || true)"

# ════════════════════════════════════════════════════════════════════════
# 부트스트랩 — 활성 색 상태가 없으면(최초/마이그레이션) 단색(blue)으로 기동(무중단 아님, 1회)
# ════════════════════════════════════════════════════════════════════════
if [ ! -f "$COLOR_FILE" ]; then
  log "!!! 활성 색 상태 없음 — 부트스트랩(단색 blue 기동, 무중단 아님). 이후 배포부터 Blue-Green."
  ensure_infra
  # 옛(단일 서비스) 스택이 있으면 orphan 으로 정리(포트 80 충돌 방지). 1회 컷오버 블립 허용.
  dc up -d --remove-orphans --force-recreate core-api-blue ledger-api-blue
  wait_healthy core-api-blue   || die "부트스트랩 core-api-blue health 실패"
  wait_healthy ledger-api-blue || die "부트스트랩 ledger-api-blue health 실패"
  write_upstream blue blue
  docker exec "$REDIS_CTN" redis-cli SET ledger:active-color blue >/dev/null || die "active-color 설정 실패"
  dc up -d nginx
  # 이미 떠 있던 nginx(재부트스트랩)면 새 active-upstream.conf 를 reload 로 반영(새로 기동된 경우엔 기동 시 로드돼 무해).
  reload_nginx || log "  nginx reload 생략/실패 — 새로 기동된 경우 정상(설정은 기동 시 로드됨)"
  echo "blue" > "$COLOR_FILE"; echo "$NEW_SHA" > "$STATE_FILE"
  log "=== 부트스트랩 완료 : color=blue sha=$NEW_SHA ==="
  dc ps; exit 0
fi

ACTIVE="$(cat "$COLOR_FILE" 2>/dev/null || echo blue)"
[ "$ACTIVE" = "blue" ] || [ "$ACTIVE" = "green" ] || ACTIVE="blue"
TARGET="$(opposite "$ACTIVE")"
log "현재 활성 색=$ACTIVE → 새 색=$TARGET (직전 SHA: ${PREV_SHA:-없음})"

ensure_infra
ensure_active_stack "$ACTIVE"

# core upstream 이 새 색으로 넘어가 옛 core 가 정지된 뒤인지 — abort 시 새 core 를 보존할지 판단.
CORE_PROMOTED=0

# ── 실패 시 정리(단계 인식 롤백) ──
#   core 승격 전: 옛 색(core+ledger)이 그대로 서비스 중 → 새 색만 정리, upstream 은 옛 색으로 확정.
#   core 승격 후: 옛 core 는 이미 정지됨 → 새 core 를 죽이면 core 0개(전면 중단)이므로 새 core 는 유지하고
#                ledger 만 옛 색으로 원복한다(옛 ledger 는 아직 살아 있음).
abort_target() {
  if [ "$CORE_PROMOTED" = "1" ]; then
    log "!!! 배포 실패(core 승격 후) — 새 core($TARGET) 유지, ledger 만 옛 색($ACTIVE)으로 원복"
    write_upstream "$TARGET" "$ACTIVE"      # core=새 색 유지, ledger=옛 색 원복
    reload_nginx || log "  nginx reload 실패 — 수동 확인 필요(active-upstream.conf: core=$TARGET ledger=$ACTIVE)"
    dc stop "ledger-api-$TARGET" 2>/dev/null || true
    die "배포 중단(부분 롤백). core=$TARGET(새 색)·ledger=$ACTIVE(옛 색) 혼합 상태, 실패 SHA=$NEW_SHA"
  fi
  log "!!! 배포 실패(core 승격 전) — 새 색($TARGET) 정리, 옛 색($ACTIVE) 유지(롤백)"
  write_upstream "$ACTIVE" "$ACTIVE"        # 중간에 써둔 upstream 흔적 제거 — 옛 색으로 확정
  reload_nginx || log "  nginx reload 실패 — 수동 확인 필요(active-upstream.conf: $ACTIVE)"
  dc stop "core-api-$TARGET" "ledger-api-$TARGET" 2>/dev/null || true
  die "배포 중단(롤백 완료). 활성=$ACTIVE 유지, 실패 SHA=$NEW_SHA"
}

# ════════════════════════════════════════════════════════════════════════
# 1) CORE 교체 (백그라운드 없음 — 스위치 + graceful 정지)
# ════════════════════════════════════════════════════════════════════════
log "[CORE] 새 색 기동: core-api-$TARGET"
dc up -d --no-deps --force-recreate "core-api-$TARGET"
wait_healthy "core-api-$TARGET" || abort_target
write_upstream "$TARGET" "$ACTIVE"        # core 만 새 색, ledger 는 아직 옛 색
reload_nginx || abort_target              # REST(core) 0초 컷오버
CORE_PROMOTED=1                           # 여기부터 옛 core 정지 — 이후 실패는 새 core 를 보존(부분 롤백)
log "[CORE] drain ${DRAIN_SECONDS}s 후 옛 색 정지"
sleep "$DRAIN_SECONDS"
dc stop "core-api-$ACTIVE"                # graceful(stop_grace_period 30s)으로 in-flight REST 마무리

# ════════════════════════════════════════════════════════════════════════
# 2) LEDGER 교체 (단일활성 핸드오버)
# ════════════════════════════════════════════════════════════════════════
log "[LEDGER] 새 색 기동: ledger-api-$TARGET (active-color=$ACTIVE → 백그라운드 OFF로 부팅)"
dc up -d --no-deps --force-recreate "ledger-api-$TARGET"
wait_healthy "ledger-api-$TARGET" || abort_target
write_upstream "$TARGET" "$TARGET"        # ledger 도 새 색
reload_nginx || abort_target              # REST(ledger) 0초 컷오버

log "[LEDGER] 옛 색 quiesce(백그라운드 정지 신호)"
docker exec "pocketstock-ledger-api-$ACTIVE" \
  curl -fsS -X POST "http://localhost:8082/internal/lifecycle/quiesce" >/dev/null \
  || log "  quiesce 호출 실패(무시) — 옛 색은 곧 정지됨"
log "[LEDGER] drain ${DRAIN_SECONDS}s (옛 색 in-flight 틱·요청 마무리)"
sleep "$DRAIN_SECONDS"
dc stop "ledger-api-$ACTIVE"              # 옛 색 정지 → LS/KIS 세션·백그라운드 종료

# 옛 색이 완전히 내려간 뒤에 새 색을 활성으로 인계(동시 active·LS 세션 중첩 없음)
docker exec "$REDIS_CTN" redis-cli SET ledger:active-color "$TARGET" >/dev/null \
  || die "active-color 전환 실패 — 수동 조치 필요(redis-cli SET ledger:active-color $TARGET)"
log "[LEDGER] active-color=$TARGET — 새 색 rearm(인덱스 재적재·구독·CUR 핀)"
docker exec "pocketstock-ledger-api-$TARGET" \
  curl -fsS -X POST "http://localhost:8082/internal/lifecycle/rearm" >/dev/null \
  || log "  rearm 호출 실패(무시) — active-color 반영됨, 백그라운드는 하트비트(≤60s)로 자동 재개"

# ---- 상태 기록 ----
echo "$TARGET"  > "$COLOR_FILE"
echo "$NEW_SHA" > "$STATE_FILE"
log "=== 배포 성공(무중단) : color=$TARGET sha=$NEW_SHA ==="
dc ps
