package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.StockDetailResponse;
import com.pocketstock.ledger.trading.dto.StockPriceResponse;
import com.pocketstock.ledger.trading.dto.StockSearchItem;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 종목마스터 검색/상세. 검색은 100% 자체 DB(tradable_stocks), 상세만 LS t1102 현재가를 합성.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    /** t1102(국내 현재가)로 시세 합성이 가능한 시장 — 그 외(해외)는 g3101 추후. */
    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final int MAX_SEARCH_LIMIT = 50;

    private final StockMapper stockMapper;
    private final StockPriceService stockPriceService;

    /** 종목 검색 — 종목명/코드 부분일치(활성 종목만). */
    @Transactional(readOnly = true)
    public List<StockSearchItem> search(Long userId, String keyword, int limit) {
        requireAuth(userId);
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검색어를 입력해주세요.");
        }
        int capped = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        return stockMapper.search(keyword.trim(), capped).stream()
                .map(s -> new StockSearchItem(
                        s.getStockCode(),
                        s.getStockName(),
                        s.getEnglishName(),
                        s.getExchange(),
                        s.getSecType(),
                        s.getCurrency(),
                        s.getLogoUrl()))
                .toList();
    }

    /** 종목 상세 — 마스터 + 현재가(국내만 t1102, 해외는 null). */
    @Transactional(readOnly = true)
    public StockDetailResponse getDetail(Long userId, String stockCode) {
        requireAuth(userId);
        TradableStock stock = stockMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }

        StockPriceResponse price = null;
        if (DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            price = stockPriceService.getDomesticPrice(userId, stockCode);
        }

        return new StockDetailResponse(
                stock.getStockCode(),
                stock.getStockName(),
                stock.getEnglishName(),
                stock.getExchange(),
                stock.getStandardCode(),
                stock.getCurrency(),
                stock.getSecType(),
                Boolean.TRUE.equals(stock.getIsFractional()),
                stock.getLogoUrl(),
                price);
    }

    private void requireAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
