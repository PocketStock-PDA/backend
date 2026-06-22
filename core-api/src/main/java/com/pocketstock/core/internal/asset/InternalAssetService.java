package com.pocketstock.core.internal.asset;

import com.pocketstock.core.internal.asset.dto.CardRoundupSummary;
import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import com.pocketstock.core.internal.asset.dto.PointSummary;
import com.pocketstock.core.internal.asset.dto.SourceDeduction;
import com.pocketstock.core.internal.asset.mapper.InternalAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalAssetService {

    private final InternalAssetMapper mapper;

    @Transactional(readOnly = true)
    public List<LinkedAccountSummary> getLinkedAccounts(Long userId, List<Long> enabledIds) {
        if (enabledIds == null || enabledIds.isEmpty()) {
            return List.of();
        }
        return mapper.findLinkedAccountsByUserAndIds(userId, enabledIds);
    }

    @Transactional(readOnly = true)
    public CardRoundupSummary getCardRoundup(Long userId, Long linkedAccountId) {
        List<Map<String, Object>> rows = mapper.findUncollectedCardTxs(userId, linkedAccountId);

        BigDecimal total = BigDecimal.ZERO;
        List<Long> ids = new java.util.ArrayList<>();

        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            BigDecimal amount = new BigDecimal(row.get("amount").toString());
            // ceil(amount / 1000) * 1000 - amount
            BigDecimal roundup = amount.divide(BigDecimal.valueOf(1000), 0, RoundingMode.CEILING)
                    .multiply(BigDecimal.valueOf(1000))
                    .subtract(amount);
            total = total.add(roundup);
            ids.add(id);
        }

        return new CardRoundupSummary(total, ids);
    }

    @Transactional
    public void markRoundupCollected(Long userId, List<Long> cardTransactionIds) {
        if (cardTransactionIds == null || cardTransactionIds.isEmpty()) {
            return;
        }
        mapper.markRoundupCollected(userId, cardTransactionIds);
    }

    @Transactional(readOnly = true)
    public PointSummary getAvailablePoints(Long userId, Long linkedAccountId) {
        BigDecimal balance = mapper.findPointBalance(userId, linkedAccountId);
        return new PointSummary(linkedAccountId, balance != null ? balance : BigDecimal.ZERO);
    }

    /**
     * 끝전 수집 확정 — 수집된 연동 계좌 잔액을 차감해 원천을 닫는다(재수집/무한복사 방지).
     * ledger-api가 원장 입금을 기록한 뒤 호출한다.
     */
    @Transactional
    public void deductAccountBalances(Long userId, List<SourceDeduction> deductions) {
        if (deductions == null) return;
        for (SourceDeduction d : deductions) {
            mapper.deductAccountBalance(userId, d.id(), d.amount());
        }
    }

    /** 포인트 수집 확정 — 수집된 연동 포인트 잔액을 차감해 원천을 닫는다. */
    @Transactional
    public void deductPointBalances(Long userId, List<SourceDeduction> deductions) {
        if (deductions == null) return;
        for (SourceDeduction d : deductions) {
            mapper.deductPointBalance(userId, d.id(), d.amount());
        }
    }
}
