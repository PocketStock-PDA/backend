package com.pocketstock.core.internal.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
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

    /** 외화(USD) 지갑 목록 — 잔돈 수집의 FX 소스. ledger-api가 전액을 CMA 달러 풀로 입금 후 차감 호출한다. */
    @Transactional(readOnly = true)
    public List<LinkedAccountSummary> getUsdWallets(Long userId) {
        return mapper.findUsdWallets(userId);
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
            int updated = mapper.deductAccountBalance(userId, d.id(), d.amount());
            requireSingleRowDeducted(updated, "연동 계좌", d);
        }
    }

    /** 포인트 수집 확정 — 수집된 연동 포인트 잔액을 차감해 원천을 닫는다. */
    @Transactional
    public void deductPointBalances(Long userId, List<SourceDeduction> deductions) {
        if (deductions == null) return;
        for (SourceDeduction d : deductions) {
            int updated = mapper.deductPointBalance(userId, d.id(), d.amount());
            requireSingleRowDeducted(updated, "연동 포인트", d);
        }
    }

    /**
     * 차감이 정확히 1건 반영됐는지 강제한다. 0건이면(대상 없음/해지/잔액 부족 등) 예외를 던져
     * 트랜잭션을 롤백시키고, 원장은 적립됐는데 원천 차감이 누락되는 부분성공(금액 유실)을 막는다.
     */
    private void requireSingleRowDeducted(int updated, String sourceLabel, SourceDeduction d) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    sourceLabel + " 잔액 차감에 실패했습니다. (id=" + d.id() + ", amount=" + d.amount() + ")");
        }
    }
}
