package com.pocketstock.ledger.config;

import com.pocketstock.ledger.kis.KisRealtimeClient;
import com.pocketstock.ledger.ls.LsRealtimeClient;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.support.MarketSession;
import com.pocketstock.ledger.trading.support.MarketSessionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 해외 실시간 구독 wiring 검증 — 클라가 종목코드(AAPL)로 구독 시
 * 세션에 따라 KIS 등록키가 자동 결정되는지(또는 CLOSED면 스킵하는지).
 */
@ExtendWith(MockitoExtension.class)
class RealtimeSubscriptionManagerTest {

    @Mock LsRealtimeClient lsClient;
    @Mock KisRealtimeClient kisClient;
    @Mock StockMapper stockMapper;
    @Mock MarketSessionResolver sessionResolver;

    RealtimeSubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new RealtimeSubscriptionManager(lsClient, kisClient, stockMapper, sessionResolver);
        lenient().when(kisClient.name()).thenReturn("KIS");
        lenient().when(stockMapper.findByCode("AAPL"))
                .thenReturn(TradableStock.builder().stockCode("AAPL").exchange("NASDAQ").build());
    }

    private void subscribe(String destination) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination(destination);
        acc.setSessionId("sess-1");
        acc.setSubscriptionId("sub-1");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        manager.onSubscribe(new SessionSubscribeEvent(this, msg));
    }

    @Test
    @DisplayName("CLOSED(장 마감): AAPL 구독해도 KIS 등록 스킵")
    void closedSkipsRegistration() {
        when(sessionResolver.current()).thenReturn(MarketSession.CLOSED);

        subscribe("/topic/foreign/quote/AAPL");

        verify(kisClient, never()).register(any(), any());
    }

    @Test
    @DisplayName("REGULAR(정규장): AAPL → KIS에 HDFSASP0 / DNASAAPL 등록")
    void regularRegistersDnas() {
        when(sessionResolver.current()).thenReturn(MarketSession.REGULAR);

        subscribe("/topic/foreign/quote/AAPL");

        verify(kisClient).register("HDFSASP0", "DNASAAPL");
    }

    @Test
    @DisplayName("DAY(주간거래): AAPL → KIS에 HDFSASP0 / RBAQAAPL 등록")
    void dayRegistersRbaq() {
        when(sessionResolver.current()).thenReturn(MarketSession.DAY);

        subscribe("/topic/foreign/quote/AAPL");

        verify(kisClient).register("HDFSASP0", "RBAQAAPL");
    }

    @Test
    @DisplayName("매핑 불가 거래소(국내 종목을 해외 토픽 구독): 예외 흡수→등록 스킵")
    void unmappedExchangeSkips() {
        when(sessionResolver.current()).thenReturn(MarketSession.REGULAR);
        when(stockMapper.findByCode("005930"))
                .thenReturn(TradableStock.builder().stockCode("005930").exchange("KOSPI").build());

        subscribe("/topic/foreign/quote/005930");

        verify(kisClient, never()).register(any(), any());
    }
}
