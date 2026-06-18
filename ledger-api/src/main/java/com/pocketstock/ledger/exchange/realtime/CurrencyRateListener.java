package com.pocketstock.ledger.exchange.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.ls.LsRealtimeListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * LS CUR(현물USD 실시간) 프레임을 {@link CurrencyRateResponse}로 매핑해
 * {@code /topic/currency/usd-krw}로 push 한다. 환전 화면의 실시간 환율 갱신용.
 *
 * <p>진입 시 REST 환율 조회({@code /api/exchange/rate}, CUR 캐시) 스냅샷으로 첫 렌더 후,
 * 이 틱으로 환율/전일대비를 갱신한다 — 필드명을 REST 응답과 맞춰 프론트가 렌더 로직을 재사용.
 *
 * <p>매 틱을 {@link CurrencyRateCache}에 먼저 기록(SSOT)한 뒤 WS로 push 한다 —
 * REST·환전 체결이 구독 없이도 같은 최신 환율을 읽는다.
 */
@Component
@RequiredArgsConstructor
public class CurrencyRateListener implements LsRealtimeListener {

    private static final String TR_CD = "CUR";
    private static final String TOPIC = "/topic/currency/usd-krw";

    private final SimpMessagingTemplate messagingTemplate;
    private final CurrencyRateCache rateCache;

    @Override
    public String trCd() {
        return TR_CD;
    }

    @Override
    public void onData(JsonNode body) {
        String baseId = body.path("base_id").asText("").trim(); // 기초자산ID (예: USD)
        if (baseId.isEmpty()) {
            baseId = "USD";
        }

        String sign = body.path("sign").asText("").trim();
        CurrencyRateResponse payload = new CurrencyRateResponse(
                baseId,                                 // 기초자산(USD)
                "KRW",
                dec(body, "price"),                     // 체결가(환율)
                signed(dec(body, "change"), sign),      // 전일대비(절대값) → 부호 적용
                LocalDateTime.now().toString());        // 수신 시각(LS는 HHmmss만 제공)

        rateCache.put(payload);                         // SSOT 갱신(REST·환전 체결 공유)
        messagingTemplate.convertAndSend(TOPIC, payload);
    }

    /** sign(4하한·5하락)이면 음수로. 절대값으로 오는 전일대비에 방향을 적용. */
    private BigDecimal signed(BigDecimal v, String sign) {
        return ("4".equals(sign) || "5".equals(sign)) ? v.negate() : v;
    }
}
