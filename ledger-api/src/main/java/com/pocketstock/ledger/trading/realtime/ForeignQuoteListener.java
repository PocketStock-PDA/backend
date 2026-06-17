package com.pocketstock.ledger.trading.realtime;

import com.pocketstock.ledger.kis.KisRealtimeListener;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * KIS HDFSASP0(해외 실시간호가) 데이터 프레임을 {@link ForeignQuoteResponse}로 매핑해
 * {@code /topic/foreign/quote/{RSYM}}로 push 한다.
 *
 * <p>필드 순서(캐럿 구분): RSYM,SYMB,ZDIV,XYMD,XHMS,KYMD,KHMS,BVOL,AVOL,BDVL,ADVL(11),
 * 이후 호가 단계별 6필드 × 10 = PBID,PASK,VBID,VASK,DBID,DASK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForeignQuoteListener implements KisRealtimeListener {

    private static final String TR_ID = "HDFSASP0";
    private static final String TOPIC_PREFIX = "/topic/foreign/quote/";
    private static final int LEVELS = 10;
    private static final int HEADER_FIELDS = 11;     // RSYM..ADVL
    private static final int FIELDS_PER_LEVEL = 6;   // PBID,PASK,VBID,VASK,DBID,DASK

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public String trId() {
        return TR_ID;
    }

    @Override
    public void onData(String[] f) {
        int required = HEADER_FIELDS + LEVELS * FIELDS_PER_LEVEL; // 71
        if (f.length < required) {
            log.warn("KIS HDFSASP0 필드 부족: {} < {}", f.length, required);
            return;
        }

        String realtimeCode = f[0]; // RSYM (구독 tr_key와 동일)
        String symbol = f[1];       // SYMB

        List<Level> asks = new ArrayList<>(LEVELS);
        List<Level> bids = new ArrayList<>(LEVELS);
        for (int n = 1; n <= LEVELS; n++) {
            int base = HEADER_FIELDS + (n - 1) * FIELDS_PER_LEVEL;
            bids.add(new Level(n, dec(f[base]), dec(f[base + 2])));     // PBID, VBID
            asks.add(new Level(n, dec(f[base + 1]), dec(f[base + 3]))); // PASK, VASK
        }

        ForeignQuoteResponse payload = new ForeignQuoteResponse(
                symbol,
                realtimeCode,
                f[4],            // XHMS 현지시간
                asks,
                bids,
                dec(f[8]),       // AVOL 매도총잔량
                dec(f[7]));      // BVOL 매수총잔량

        messagingTemplate.convertAndSend(TOPIC_PREFIX + realtimeCode, payload);
    }

    /** KIS 숫자 필드(문자/공백 가능) → BigDecimal. 빈 값은 0. */
    private BigDecimal dec(String s) {
        String t = (s == null) ? "" : s.trim();
        return t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t);
    }
}
