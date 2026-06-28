package com.pocketstock.core.recommendations.maturity;

import com.pocketstock.core.recommendations.maturity.dto.DividendStockItem;
import com.pocketstock.core.recommendations.maturity.dto.DividendStockRow;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.recommendations.maturity.dto.MaturityRecommendationResponse;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountDto;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountRow;
import com.pocketstock.core.recommendations.maturity.mapper.MaturityRecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaturityRecommendationService {

    private final MaturityRecommendationMapper mapper;

    /** 만기 굴리기 대상 예적금 목록(미래 만기·임박 순) — 선택 화면용. */
    public List<TriggerAccountDto> listAccounts(Long userId) {
        return mapper.findMaturityAccounts(userId).stream()
                .map(a -> new TriggerAccountDto(
                        a.getAccountId(),
                        a.getAccountName(),
                        a.getMaturityDate(),
                        a.getPrincipalAmount(),
                        a.getDaysUntilMaturity(),
                        toPct(a.getInterestRate())))
                .toList();
    }

    /**
     * 배당주 추천 — {@code accountId} 지정 시 그 예적금(소유·예적금·미래 만기 검증) 기준,
     * 없으면 가장 가까운 만기 계좌를 자동 선택(자산 페이지 알림 호환).
     */
    public MaturityRecommendationResponse recommend(Long userId, Long accountId) {
        TriggerAccountRow account;
        if (accountId != null) {
            // 사용자가 고른 계좌 — 없거나(만료·미소유·예적금 아님) 대상 아님은 명시적 404로(200+null 금지).
            account = mapper.findMaturityAccountById(userId, accountId);
            if (account == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "선택한 예적금을 찾을 수 없거나 만기 굴리기 대상이 아닙니다.");
            }
        } else {
            // 자동 선택 — 도래 계좌가 없으면 빈 상태(정상)로 null 반환.
            account = mapper.findUpcomingMaturityAccount(userId);
            if (account == null) return null;
        }

        BigDecimal interestRatePct = account.getInterestRate()
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        List<DividendStockRow> rows = mapper.findDividendStocksAboveRate(interestRatePct);

        String reason = String.format("현재 예금 이율(%.1f%%)보다 높은 배당 수익률",
                interestRatePct.doubleValue());

        List<DividendStockItem> items = rows.stream()
                .map(r -> new DividendStockItem(
                        r.getStockCode(),
                        r.getStockName(),
                        r.getCategory(),
                        r.getMarket(),
                        r.getDividendYield(),
                        parseTags(r.getTags()),
                        r.getExDividendDate(),
                        r.getPerShareDividend(),
                        r.getPayDate(),
                        reason
                ))
                .toList();

        TriggerAccountDto triggerAccount = new TriggerAccountDto(
                account.getAccountId(),
                account.getAccountName(),
                account.getMaturityDate(),
                account.getPrincipalAmount(),
                account.getDaysUntilMaturity(),
                interestRatePct
        );

        return new MaturityRecommendationResponse(triggerAccount, items);
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.asList(tags.split("\\|"));
    }

    /** 연이율(0.035) → 표시용 퍼센트(3.50). */
    private BigDecimal toPct(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
