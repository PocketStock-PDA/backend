package com.pocketstock.ledger.trading.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 시세 스냅샷 last-known 캐시 (#128) — 종목·종류별 마지막 응답을 Redis에 보관한다.
 * 장 마감/단절로 벤더가 빈 호가창을 주거나 호출이 실패해도 마지막 스냅샷을 보여주기 위함.
 * 환율 {@code exchange.CurrencyRateCache}와 같은 패턴(덮어쓰기·예외삼킴·콜드면 null).
 *
 * <p>키 = {@code snapshot:{type}:{symbol}}. TTL 3일 — 매 put마다 갱신되므로 활발한 종목은
 * 만료되지 않고(주말 공백 커버), 장기 미거래 종목 키만 정리된다. 만료돼도 read-through가 벤더
 * REST로 자동 복구하므로 손해 없음. 멀티 인스턴스 공유 + 재시작 유지(Redis).
 *
 * <p>⚠️ 보여주기 전용 — 체결 판정엔 쓰면 안 됨(과거가 마법체결 = #110이 막으려는 것).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSnapshotCache {

    private static final String KEY_PREFIX = "snapshot:";
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 최신 스냅샷 덮어쓰기 — TTL 갱신. 실패해도 본 흐름을 막지 않음(로그만). */
    public <T> void put(String type, String symbol, T payload) {
        try {
            redis.opsForValue().set(key(type, symbol), objectMapper.writeValueAsString(payload), TTL);
        } catch (Exception e) {
            log.warn("시세 스냅샷 캐시 저장 실패 type={} symbol={}: {}", type, symbol, e.getMessage());
        }
    }

    /** 마지막 스냅샷 — 없거나(콜드) 조회 실패면 null. */
    public <T> T get(String type, String symbol, Class<T> clazz) {
        try {
            String json = redis.opsForValue().get(key(type, symbol));
            return json == null ? null : objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("시세 스냅샷 캐시 조회 실패 type={} symbol={}: {}", type, symbol, e.getMessage());
            return null;
        }
    }

    /**
     * read-through(vendor-first + 캐시 폴백) — 벤더 호출이 성공하고 결과가 usable이면 캐시 갱신 후 반환,
     * 빈/무효 응답(usable=false)이거나 호출이 실패하면 마지막 캐시 스냅샷을 반환한다.
     * 캐시가 없을 때는: 벤더가 응답은 했으나 빈/무효였으면 그 응답을 그대로 반환하고(성공한 빈 응답을
     * 오류로 만들지 않음 — 마감·신규 종목의 빈 호가창 등 #128 이전 동작 보존), 벤더 호출 자체가
     * 실패(예외)했으면 EXTERNAL_API_ERROR를 던진다. usable=false 응답은 캐시에 쓰지 않는다(빈 값 오염 방지).
     * 벤더 호출 예외만 삼킨다(인증·존재검증 같은 사전 검증은 호출 전에 끝낸 뒤 진입할 것).
     *
     * @param usable 벤더 응답이 "보여줄 만한가" 판정(예: 빈 호가창이면 false → 캐시 폴백)
     */
    public <T> T readThrough(String type, String symbol, Supplier<T> vendorCall, Predicate<T> usable,
                             Class<T> clazz, String label) {
        T fresh = null;
        try {
            fresh = vendorCall.get();
        } catch (Exception e) {
            log.warn("{} 벤더 조회 실패 symbol={} — 캐시 폴백 시도: {}", label, symbol, e.getMessage());
        }
        if (fresh != null && usable.test(fresh)) {
            put(type, symbol, fresh);   // 신선 → 캐시 갱신
            return fresh;
        }
        T cached = get(type, symbol, clazz);
        if (cached != null) {
            log.debug("{} 캐시 폴백(벤더 빈 응답/실패) symbol={}", label, symbol);
            return cached;   // asOf가 과거값 → 프론트가 staleness 표시
        }
        if (fresh != null) {
            return fresh;    // 캐시 없음 + 벤더가 빈 응답 → 그거라도 반환
        }
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, label + " 정보를 조회할 수 없습니다.");
    }

    private String key(String type, String symbol) {
        return KEY_PREFIX + type + ":" + symbol;
    }
}
