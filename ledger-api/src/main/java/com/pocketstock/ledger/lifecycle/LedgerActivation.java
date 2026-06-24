package com.pocketstock.ledger.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 무중단 배포(Blue-Green) 단일활성 게이트.
 *
 * <p>ledger 백그라운드(LS/KIS 실시간·매칭엔진·전 {@code @Scheduled})는 단일 인스턴스 전제다(분산락 없음).
 * 색 전환 중 두 색이 동시에 백그라운드를 돌리면 중복 체결·중복 스케줄 발화가 나므로 "동시에 한 색만
 * 활성"을 보장한다 — 활성 색은 Redis 키 {@code ledger:active-color} 가 가리키고, 각 인스턴스는 자기
 * 색({@code DEPLOY_COLOR})이 그와 같을 때만 {@link #isActive()} true.
 *
 * <p>핸드오버는 deploy-release.sh 가 조율한다: 옛 색 quiesce({@link #startDraining()}) → drain →
 * active-color 전환 → 새 색 rearm({@link #refreshNow()} 후 백그라운드 재무장). quiesce 는 즉시
 * 백그라운드를 끄고(진행 틱은 마무리), 옛 색이 정지되면 새 색이 활성으로 인계받는다.
 *
 * <p>키 부재 시 기본 active(현 단일 인스턴스 행동 보존). {@code DEPLOY_COLOR} 미설정(로컬/단일 실행)도
 * active. onTick 등 핫패스에서 매번 Redis 를 치지 않도록 활성 색을 짧게 캐시한다.
 *
 * <p>리더 선출/자동 페일오버(상시 멀티 인스턴스)는 별도 후속(backend#24) — 이 게이트는 "배포 순서로
 * 단일활성 보장"이라 단일 EC2·평소 1벌·배포때만 잠깐 2벌 시나리오를 대상으로 한다.
 */
@Slf4j
@Component
public class LedgerActivation {

    static final String ACTIVE_COLOR_KEY = "ledger:active-color";

    private final StringRedisTemplate redis;
    private final String myColor;

    /** quiesce 시 즉시 비활성(진행 틱은 마무리, 새 틱 차단). 로컬 신호라 즉발. */
    private volatile boolean draining = false;
    /** active-color 캐시(핫패스 Redis 회피). 주기 갱신 + rearm/quiesce 시 즉시 동기화. */
    private volatile String cachedActiveColor;

    public LedgerActivation(StringRedisTemplate redis,
                            @Value("${DEPLOY_COLOR:}") String deployColor) {
        this.redis = redis;
        this.myColor = (deployColor == null) ? "" : deployColor.trim();
        this.cachedActiveColor = readActiveColor();
    }

    /** 이 인스턴스가 백그라운드(실시간·매칭·스케줄러)를 돌려도 되는지. */
    public boolean isActive() {
        if (draining) {
            return false;   // quiesce 중 — 새 백그라운드 작업 차단
        }
        String active = cachedActiveColor;
        if (active == null || active.isBlank()) {
            return true;    // 키 부재 → 기본 active(단일 인스턴스 행동 보존)
        }
        if (myColor.isBlank()) {
            return true;    // 색 미설정(로컬/단일 실행) → active
        }
        return myColor.equals(active);
    }

    public String myColor() {
        return myColor;
    }

    /** 드레인 시작 — 즉시 비활성(옛 색 정지 직전 호출). 진행 중 틱은 마무리된다. */
    public void startDraining() {
        this.draining = true;
        log.info("[Blue-Green] color={} quiesce — 백그라운드 비활성(진행 틱 마무리 후 정지 예정)", myColor);
    }

    /** active-color 캐시를 즉시 동기화(rearm 직후 호출 — 주기 갱신 안 기다리고 바로 반영). */
    public void refreshNow() {
        this.draining = false;
        this.cachedActiveColor = readActiveColor();
        log.info("[Blue-Green] color={} 활성 상태 갱신 — active-color={} isActive={}",
                myColor, cachedActiveColor, isActive());
    }

    /** 활성 색 캐시 주기 갱신(배포 외엔 변동 없음 — 핫패스 Redis 회피용). 게이트 자체라 게이팅 안 함. */
    @Scheduled(fixedDelay = 3000)
    public void refreshActiveColor() {
        this.cachedActiveColor = readActiveColor();
    }

    private String readActiveColor() {
        try {
            return redis.opsForValue().get(ACTIVE_COLOR_KEY);
        } catch (Exception e) {
            // Redis 일시 장애 — 캐시 유지(다음 주기 재시도). 부팅 시 예외면 null(기본 active).
            log.warn("[Blue-Green] active-color 조회 실패 — 캐시 유지: {}", e.getMessage());
            return cachedActiveColor;
        }
    }
}
