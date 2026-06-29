package com.pocketstock.core.recommendations.maturity;

import com.pocketstock.core.recommendations.maturity.dto.DividendStockItem;
import com.pocketstock.core.recommendations.maturity.dto.DividendStockRow;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.recommendations.maturity.dto.MaturityRecommendationResponse;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountDto;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountRow;
import com.pocketstock.core.recommendations.maturity.mapper.MaturityRecommendationMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaturityRecommendationService {

    private final MaturityRecommendationMapper mapper;
    private final LedgerFeignClient ledgerFeignClient;

    /**
     * 만기 굴리기 대상 예적금 목록(미래 만기·임박 순).
     * 이미 자금 굴리기로 선택한(활성 예약) 계좌도 포함하되 {@code reserved=true}로 표시한다 —
     * 선택 탭은 이를 숨기고, 전환내역은 계좌 정보(이름·D-day 등) 매칭에 그대로 쓴다.
     */
    public List<TriggerAccountDto> listAccounts(Long userId) {
        Set<Long> reserved = reservedAccountIds(userId);
        return mapper.findMaturityAccounts(userId).stream()
                .map(a -> new TriggerAccountDto(
                        a.getAccountId(),
                        a.getAccountName(),
                        a.getMaturityDate(),
                        a.getPrincipalAmount(),
                        a.getDaysUntilMaturity(),
                        toPct(a.getInterestRate()),
                        reserved.contains(a.getAccountId())))
                .toList();
    }

    /**
     * 배당주 추천 — {@code accountId} 지정 시 그 예적금(소유·예적금·미래 만기 검증) 기준,
     * 없으면 가장 가까운 만기 계좌를 자동 선택(자산 페이지 알림 호환).
     */
    public MaturityRecommendationResponse recommend(Long userId, Long accountId) {
        Set<Long> reserved = reservedAccountIds(userId);
        TriggerAccountRow account;
        if (accountId != null) {
            // 이미 자금 굴리기로 선택한(활성 예약) 계좌는 재선택 불가 — 전환내역에만 노출.
            if (reserved.contains(accountId)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "이미 자금 굴리기로 선택한 예적금입니다. 전환내역에서 확인하세요.");
            }
            // 사용자가 고른 계좌 — 없거나(만료·미소유·예적금 아님) 대상 아님은 명시적 404로(200+null 금지).
            account = mapper.findMaturityAccountById(userId, accountId);
            if (account == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "선택한 예적금을 찾을 수 없거나 만기 굴리기 대상이 아닙니다.");
            }
        } else {
            // 자동 선택 — 활성 예약 계좌는 건너뛰고 가장 가까운 만기 계좌를 고른다. 없으면 빈 상태(정상)로 null.
            account = mapper.findMaturityAccounts(userId).stream()
                    .filter(a -> !reserved.contains(a.getAccountId()))
                    .findFirst().orElse(null);
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
                interestRatePct,
                false   // 추천 진입 계좌는 미예약(예약된 계좌는 recommend 진입 시 CONFLICT로 차단)
        );

        return new MaturityRecommendationResponse(triggerAccount, items);
    }

    /** 이미 자금 굴리기로 선택한(활성 예약) 계좌 id — ledger 조회. 장애 시 빈 집합(후보 그대로 노출). */
    private Set<Long> reservedAccountIds(Long userId) {
        try {
            return new HashSet<>(ledgerFeignClient.getReservedMaturityAccountIds(userId));
        } catch (Exception e) {
            return Set.of();
        }
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
