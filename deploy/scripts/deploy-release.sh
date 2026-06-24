#!/usr/bin/env bash
# deploy-release.sh — EC2에서 실행하는 릴리스 배포 스크립트 (CICD가 SSH로 호출).
#
#   ./deploy-release.sh <40자리-commit-sha>
#
# 동작: ECR 로그인 → 해당 SHA 이미지 pull → core→ledger 순차 교체 →
#       각 서비스 /actuator/health 가 healthy 될 때까지 대기 →
#       실패하면 직전 성공 SHA 이미지로 자동 롤백.
# 빌드는 하지 않는다(이미지는 CI에서 ECR로 push됨).
set -euo pipefail

# ---- 경로/상수 (EC2 배치 기준: 레포가 ~/app 에 git clone 되어 있음) ----
APP_DIR="${APP_DIR:-$HOME/app}"
COMPOSE_FILE="$APP_DIR/deploy/compose/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/deploy/compose/.env"
STATE_FILE="$APP_DIR/deploy/compose/.released_sha"   # 직전 성공 SHA 기록(롤백용)
LOCK_FILE="/tmp/pocketstock-deploy.lock"
PROJECT="pocketstock"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-150}"              # 서비스당 health 대기 한도(초)

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

dc() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

# 한 서비스가 healthy 될 때까지 대기 (앱 포트는 호스트 미노출이라 컨테이너 healthcheck 상태로 판정)
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

# 지정 TAG로 core→ledger 순차 교체(health 게이트). 실패 시 비0 반환.
release_tag() {
  local tag="$1"
  export IMAGE_TAG="$tag"
  log "이미지 pull (tag=$tag)"
  dc pull core-api ledger-api

  # 의존성 redis 먼저 기동(cold start 대비; 이미 떠 있으면 무변경).
  # core/ledger는 --no-deps로 교체해 매 배포마다 redis를 재시작하지 않음.
  log "redis 기동(필요 시)"
  dc up -d redis
  wait_healthy redis || return 1

  log "core-api 교체"
  dc up -d --no-deps --force-recreate core-api
  wait_healthy core-api || return 1

  log "ledger-api 교체"
  dc up -d --no-deps --force-recreate ledger-api
  wait_healthy ledger-api || return 1

  # 게이트웨이는 두 앱이 healthy된 뒤 기동(이미 떠 있으면 유지)
  dc up -d nginx
  return 0
}

PREV_SHA=""
[ -f "$STATE_FILE" ] && PREV_SHA="$(cat "$STATE_FILE" 2>/dev/null || true)"

log "=== 배포 시작 : $NEW_SHA (직전 성공: ${PREV_SHA:-없음}) ==="
log "ECR 로그인: $ECR_REGISTRY"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# compose 구문 검증(이미지 변수 치환 포함)
IMAGE_TAG="$NEW_SHA" dc config -q || die "compose 설정 검증 실패"

if release_tag "$NEW_SHA"; then
  echo "$NEW_SHA" > "$STATE_FILE"
  log "=== 배포 성공 : $NEW_SHA ==="
  dc ps
  exit 0
fi

log "!!! 배포 실패 — 롤백 시도"
if [ -n "$PREV_SHA" ] && [ "$PREV_SHA" != "$NEW_SHA" ]; then
  if release_tag "$PREV_SHA"; then
    log "=== 롤백 성공 : 직전 SHA $PREV_SHA 로 복구됨 (이번 릴리스 $NEW_SHA 는 미반영) ==="
    dc ps
    exit 1
  fi
  die "롤백까지 실패 — 수동 조치 필요. 직전 SHA=$PREV_SHA, 실패 SHA=$NEW_SHA"
fi
die "직전 성공 SHA가 없어 롤백 불가 — 수동 조치 필요. 실패 SHA=$NEW_SHA"
