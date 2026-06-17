package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.dto.HoldingResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 보유 종목·잔고 조회. 평가액·수익률은 추후(현재가 합성/daily_valuations).
 */
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingMapper holdingMapper;

    @Transactional(readOnly = true)
    public List<HoldingResponse> getHoldings(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return holdingMapper.findByUserId(userId).stream()
                .map(h -> new HoldingResponse(h.getStockCode(), h.getQuantity(), h.getAvgBuyPrice(), h.getCurrency()))
                .toList();
    }
}
