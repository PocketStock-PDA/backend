package com.pocketstock.core.internal.asset;

import com.pocketstock.core.internal.asset.dto.CardRoundupSummary;
import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import com.pocketstock.core.internal.asset.dto.PointSummary;
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
            // ceil(amount / 100) * 100 - amount
            BigDecimal roundup = amount.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                    .multiply(BigDecimal.valueOf(100))
                    .subtract(amount);
            total = total.add(roundup);
            ids.add(id);
        }

        return new CardRoundupSummary(total, ids);
    }

    @Transactional
    public void markRoundupCollected(List<Long> cardTransactionIds) {
        if (cardTransactionIds == null || cardTransactionIds.isEmpty()) {
            return;
        }
        mapper.markRoundupCollected(cardTransactionIds);
    }

    @Transactional(readOnly = true)
    public PointSummary getAvailablePoints(Long userId, Long linkedAccountId) {
        BigDecimal balance = mapper.findPointBalance(userId, linkedAccountId);
        return new PointSummary(linkedAccountId, balance != null ? balance : BigDecimal.ZERO);
    }
}
