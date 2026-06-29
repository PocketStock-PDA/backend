package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.domain.FxAutoSetting;
import com.pocketstock.ledger.exchange.mapper.FxAutoSettingMapper;
import com.pocketstock.ledger.trading.domain.DividendReinvestSetting;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.DividendReinvestRequest;
import com.pocketstock.ledger.trading.dto.DividendReinvestResponse;
import com.pocketstock.ledger.trading.mapper.DividendReinvestSettingMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 배당 자동 재투자(DRIP) 토글 설정 — 종목별 ON/OFF. 실제 재투자는 {@code DividendPayoutScheduler}가 배당 지급 시 집행.
 * 국내(KRW)·해외(USD) 배당주 모두 지원. 해외는 재투자 시 자동환전(KRW→USD)이 필요해 켤 때 자동환전 ON을 요구한다.
 * 토글 ON = 배당 지급 사전동의.
 */
@Service
@RequiredArgsConstructor
public class DividendReinvestService {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");

    private final DividendReinvestSettingMapper settingMapper;
    private final StockMapper stockMapper;
    private final FxAutoSettingMapper fxAutoSettingMapper;

    /** DRIP 토글 ON/OFF 설정(upsert). 종목 존재·국내 검증. */
    @Transactional
    public DividendReinvestResponse setEnabled(Long userId, DividendReinvestRequest req) {
        requireUser(userId);
        if (req == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 본문이 필요합니다.");
        }
        if (req.enabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "enabled 값이 필요합니다.");
        }
        TradableStock stock = resolveStock(req.stockCode());
        // 해외 DRIP을 켤 때는 재투자(USD 매수) 충당을 위한 자동환전이 켜져 있어야 한다(만기예약과 동일).
        if (Boolean.TRUE.equals(req.enabled()) && OVERSEAS_EXCHANGES.contains(stock.getExchange())) {
            requireAutoFxEnabled(userId);
        }
        DividendReinvestSetting setting = DividendReinvestSetting.builder()
                .userId(userId)
                .stockCode(stock.getStockCode())
                .isEnabled(req.enabled())
                .build();
        settingMapper.upsert(setting);
        setting.setStockName(stock.getStockName());
        return DividendReinvestResponse.from(setting);
    }

    /** 내 DRIP 설정 목록(종목별 ON/OFF). */
    @Transactional(readOnly = true)
    public List<DividendReinvestResponse> list(Long userId) {
        requireUser(userId);
        return settingMapper.findByUserId(userId).stream()
                .map(DividendReinvestResponse::from)
                .toList();
    }

    private TradableStock resolveStock(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종목코드를 입력해주세요.");
        }
        TradableStock stock = stockMapper.findByCode(stockCode.trim());
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        String exchange = stock.getExchange();
        if (!DOMESTIC_EXCHANGES.contains(exchange) && !OVERSEAS_EXCHANGES.contains(exchange)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "배당 재투자를 지원하지 않는 거래소입니다: " + exchange);
        }
        return stock;
    }

    /** 해외 DRIP 가드 — 재투자 시 자동환전(KRW→USD)으로 달러를 채워야 한다(만기예약과 동일). */
    private void requireAutoFxEnabled(Long userId) {
        FxAutoSetting setting = fxAutoSettingMapper.findByUserId(userId);
        if (setting == null || !Boolean.TRUE.equals(setting.getIsAutoEnabled())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "해외 배당주 재투자는 자동환전을 켜야 가능합니다. 환전 설정에서 자동환전을 켜주세요.");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
}
