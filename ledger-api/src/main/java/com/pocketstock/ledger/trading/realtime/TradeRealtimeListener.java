package com.pocketstock.ledger.trading.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketstock.ledger.ls.LsRealtimeListener;
import com.pocketstock.ledger.trading.dto.StockTradeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * LS US3(통합 체결) 실시간 프레임을 {@link StockTradeResponse}로 매핑해
 * {@code /topic/stock/trade/{stockCode}}로 push 한다. 가격은 KRX+NXT 통합 체결값.
 *
 * <p>진입 시 REST 현재가({@code /price}, t1102) 스냅샷으로 첫 렌더 후, 이 틱으로
 * 현재가/등락/시고저를 갱신한다 — 필드명을 t1102와 맞춰 프론트가 렌더링 로직을 재사용.
 */
@Component
@RequiredArgsConstructor
public class TradeRealtimeListener implements LsRealtimeListener {

    private static final String TR_CD = "US3";
    private static final String TOPIC_PREFIX = "/topic/stock/trade/";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public String trCd() {
        return TR_CD;
    }

    @Override
    public void onData(JsonNode body) {
        String stockCode = body.path("shcode").asText("").trim();
        if (stockCode.isEmpty()) {
            return;
        }

        String sign = body.path("sign").asText("").trim();
        StockTradeResponse payload = new StockTradeResponse(
                stockCode,
                body.path("chetime").asText(""),       // 체결시간 HHmmss
                dec(body, "price"),                     // 현재가(체결가)
                signed(dec(body, "change"), sign),      // 전일대비(절대값) → 부호 적용
                signed(dec(body, "drate"), sign),       // 등락율(절대값) → 부호 적용
                dec(body, "open"),                      // 시가
                dec(body, "high"),                      // 고가
                dec(body, "low"),                       // 저가
                lng(body, "volume"),                    // 누적거래량
                lng(body, "cvolume"),                   // 이번 틱 체결량(WS 전용)
                body.path("cgubun").asText("").trim(),  // 체결구분 +매수/-매도(WS 전용)
                dec(body, "cpower"));                   // 체결강도(WS 전용)

        messagingTemplate.convertAndSend(TOPIC_PREFIX + stockCode, payload);
    }

    /** LS 숫자 필드(문자/공백 가능) → BigDecimal. 빈 값은 0. */
    private BigDecimal dec(JsonNode body, String field) {
        String text = body.path(field).asText("").trim();
        return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    /** LS 수량 필드 → long. 빈 값은 0. */
    private long lng(JsonNode body, String field) {
        String text = body.path(field).asText("").trim();
        return text.isEmpty() ? 0L : new BigDecimal(text).longValue();
    }

    /** sign(4하한·5하락)이면 음수로. 절대값으로 오는 전일대비·등락율에 방향을 적용. */
    private BigDecimal signed(BigDecimal v, String sign) {
        return ("4".equals(sign) || "5".equals(sign)) ? v.negate() : v;
    }
}
