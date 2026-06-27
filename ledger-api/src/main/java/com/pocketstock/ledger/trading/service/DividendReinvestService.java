package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
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
 * 국내(KRW) 배당주 전용(만기예약과 동일 스코프). 토글 ON = 배당 지급 사전동의.
 */
@Service
@RequiredArgsConstructor
public class DividendReinvestService {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");

    private final DividendReinvestSettingMapper settingMapper;
    private final StockMapper stockMapper;

    /** DRIP 토글 ON/OFF 설정(upsert). 종목 존재·국내 검증. */
    @Transactional
    public DividendReinvestResponse setEnabled(Long userId, DividendReinvestRequest req) {
        requireUser(userId);
        if (req.enabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "enabled 값이 필요합니다.");
        }
        TradableStock stock = resolveDomesticStock(req.stockCode());
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

    private TradableStock resolveDomesticStock(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종목코드를 입력해주세요.");
        }
        TradableStock stock = stockMapper.findByCode(stockCode.trim());
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        if (!DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배당 재투자는 현재 국내 배당주만 지원합니다.");
        }
        return stock;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
}
