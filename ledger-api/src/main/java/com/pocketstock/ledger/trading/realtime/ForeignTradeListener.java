package com.pocketstock.ledger.trading.realtime;

import com.pocketstock.ledger.kis.KisRealtimeListener;
import com.pocketstock.ledger.trading.dto.ForeignTradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.pocketstock.ledger.trading.support.MarketFields.dec;
import static com.pocketstock.ledger.trading.support.MarketFields.lng;

/**
 * KIS HDFSCNT0(해외 실시간지연체결가) 데이터 프레임을 {@link ForeignTradeResponse}로 매핑해
 * {@code /topic/foreign/transaction/{RSYM}}로 push 한다.
 *
 * <p>필드 순서(캐럿 구분, 0-base):
 * RSYM0 SYMB1 ZDIV2 TYMD3 XYMD4 XHMS5 KYMD6 KHMS7 OPEN8 HIGH9 LOW10 LAST11
 * SIGN12 DIFF13 RATE14 PBID15 PASK16 VBID17 VASK18 EVOL19 TVOL20 TAMT21
 * BIVL22 ASVL23 STRN24 MTYP25.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForeignTradeListener implements KisRealtimeListener {

    private static final String TR_ID = "HDFSCNT0";
    private static final String TOPIC_PREFIX = "/topic/foreign/transaction/";
    private static final int REQUIRED_FIELDS = 26;

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public String trId() {
        return TR_ID;
    }

    @Override
    public void onData(String[] f) {
        if (f.length < REQUIRED_FIELDS) {
            log.warn("KIS HDFSCNT0 필드 부족: {} < {}", f.length, REQUIRED_FIELDS);
            return;
        }

        String realtimeCode = f[0]; // RSYM (구독 tr_key와 동일)
        String sign = f[12];        // SIGN 1상한·2상승·3보합·4하한·5하락
        ForeignTradeResponse payload = new ForeignTradeResponse(
                f[1],            // SYMB
                realtimeCode,
                f[5],            // XHMS 현지시간
                dec(f[11]),      // LAST 현재가 → currentPrice
                signed(dec(f[13]), sign), // DIFF 전일대비(절대값) → SIGN 부호 적용 → changePrice
                dec(f[14]),      // RATE 등락율(부호 포함) → changeRate
                dec(f[8]),       // OPEN → openPrice
                dec(f[9]),       // HIGH → highPrice
                dec(f[10]),      // LOW → lowPrice
                lng(f[20]),      // TVOL 누적거래량 → volume
                lng(f[19]),      // EVOL 이번 틱 체결량 → lastTradeVolume
                dec(f[24]));     // STRN 체결강도 → tradeStrength

        messagingTemplate.convertAndSend(TOPIC_PREFIX + realtimeCode, payload);
    }

    /** SIGN(4하한·5하락)이면 음수로. 절대값으로 오는 전일대비에 방향을 적용. */
    private BigDecimal signed(BigDecimal v, String sign) {
        return ("4".equals(sign) || "5".equals(sign)) ? v.negate() : v;
    }
}

