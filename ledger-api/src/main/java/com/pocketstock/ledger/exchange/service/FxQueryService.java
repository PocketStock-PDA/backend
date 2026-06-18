package com.pocketstock.ledger.exchange.service;

import com.pocketstock.ledger.exchange.dto.response.FxHistoryResponse;
import com.pocketstock.ledger.exchange.mapper.FxTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 환전 이력 조회 서비스 — fx_transactions를 사용자/기간 없이 최신순 페이징.
 */
@Service
@RequiredArgsConstructor
public class FxQueryService {

    private final FxTransactionMapper fxMapper;

    @Transactional(readOnly = true)
    public FxHistoryResponse getHistory(Long userId, int page, int size) {
        List<FxHistoryResponse.Item> items = fxMapper.findByUser(userId, page * size, size)
                .stream()
                .map(FxHistoryResponse.Item::from)
                .toList();
        long total = fxMapper.countByUser(userId);
        return new FxHistoryResponse(items, page, total);
    }
}
