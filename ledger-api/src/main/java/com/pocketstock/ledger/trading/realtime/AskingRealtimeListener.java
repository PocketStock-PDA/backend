package com.pocketstock.ledger.trading.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketstock.ledger.ls.LsRealtimeListener;
import com.pocketstock.ledger.trading.dto.AskingResponse;
import com.pocketstock.ledger.trading.dto.AskingResponse.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * LS UH1(통합호가잔량) 실시간 프레임을 {@link AskingResponse}로 매핑해
 * {@code /topic/asking/{stockCode}}로 push 한다. 잔량은 통합(unt_*) 기준.
 */
@Component
@RequiredArgsConstructor
public class AskingRealtimeListener implements LsRealtimeListener {

    private static final String TR_CD = "UH1";
    private static final String TOPIC_PREFIX = "/topic/asking/";
    private static final int LEVELS = 10;

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

        List<Level> asks = new ArrayList<>(LEVELS);
        List<Level> bids = new ArrayList<>(LEVELS);
        for (int i = 1; i <= LEVELS; i++) {
            asks.add(new Level(i, dec(body, "offerho" + i), dec(body, "unt_offerrem" + i)));
            bids.add(new Level(i, dec(body, "bidho" + i), dec(body, "unt_bidrem" + i)));
        }

        AskingResponse payload = new AskingResponse(
                stockCode,
                body.path("hotime").asText(""),
                asks,
                bids,
                dec(body, "unt_totofferrem"),
                dec(body, "unt_totbidrem"),
                dec(body, "volume"));

        messagingTemplate.convertAndSend(TOPIC_PREFIX + stockCode, payload);
    }
}
