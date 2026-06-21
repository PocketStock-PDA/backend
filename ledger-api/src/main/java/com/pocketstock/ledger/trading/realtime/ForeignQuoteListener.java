package com.pocketstock.ledger.trading.realtime;

import com.pocketstock.ledger.kis.KisRealtimeListener;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse;
import com.pocketstock.ledger.trading.dto.ForeignQuoteResponse.Level;
import com.pocketstock.ledger.trading.matching.ForeignQuoteTick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;

/**
 * KIS HDFSASP0(해외 실시간호가) 데이터 프레임을 {@link ForeignQuoteResponse}로 매핑해
 * {@code /topic/foreign/quote/{stock_code}}로 push 한다.
 * 토픽 키는 세션에 따라 바뀌는 RSYM이 아니라 안정적인 SYMB(=stock_code, 예 AAPL)를 쓴다 —
 * 클라는 종목코드만 알면 정규장/주간 무관하게 같은 토픽으로 수신한다.
 *
 * <p>같은 틱을 {@link ForeignQuoteTick}로도 발행해 지정가 PENDING 매칭 엔진이 cross 판정에 쓴다
 * (클라 팬아웃과 서버 내부 매칭이 한 프레임을 공유 — 국내 {@code AskingRealtimeListener}와 동형).
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
    private final ApplicationEventPublisher eventPublisher;

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
                dec(f[7]),       // BVOL 매수총잔량
                Instant.now().toString());   // asOf — 실시간 push라 항상 현재 시각

        // 토픽 키 = SYMB(안정적 stock_code). 구독 tr_key(RSYM)는 세션에 따라 D/R로 바뀌므로 토픽엔 안 씀.
        messagingTemplate.convertAndSend(TOPIC_PREFIX + symbol, payload);

        // 서버 내부 매칭 엔진용 — 같은 사다리를 배열로 발행(rank 1 = 최우선). 키는 SYMB(=stockCode).
        eventPublisher.publishEvent(new ForeignQuoteTick(symbol,
                prices(asks), volumes(asks), prices(bids), volumes(bids)));
    }

    private static BigDecimal[] prices(List<Level> levels) {
        return levels.stream().map(Level::price).toArray(BigDecimal[]::new);
    }

    private static BigDecimal[] volumes(List<Level> levels) {
        return levels.stream().map(Level::volume).toArray(BigDecimal[]::new);
    }
}
