package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.asset.dto.CategorySpending;
import com.pocketstock.core.asset.dto.SpendingReportResponse;
import com.pocketstock.core.asset.dto.SpendingResponse;
import com.pocketstock.core.asset.mapper.SpendingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private final SpendingMapper spendingMapper;

    public SpendingResponse getSpending(Long userId, Integer year, Integer month) {
        validate(year, month);

        LocalDateTime from = null;
        LocalDateTime to   = null;

        if (year != null && month != null) {
            LocalDate start = LocalDate.of(year, month, 1);
            from = start.atStartOfDay();
            to   = start.plusMonths(1).atStartOfDay();
        } else if (year != null) {
            LocalDate start = LocalDate.of(year, 1, 1);
            from = start.atStartOfDay();
            to   = start.plusYears(1).atStartOfDay();
        }

        List<CategoryAmountRow> rows = spendingMapper.findCategorySpending(userId, from, to);

        BigDecimal total = rows.stream()
                .map(CategoryAmountRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpending> categories = rows.stream()
                .map(r -> new CategorySpending(
                        r.getCategory(),
                        r.getAmount(),
                        total.compareTo(BigDecimal.ZERO) == 0
                                ? BigDecimal.ZERO
                                : r.getAmount()
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(total, 1, RoundingMode.HALF_UP)
                ))
                .toList();

        return new SpendingResponse(total, categories);
    }

    private static final Map<String, String> SAVING_TIPS = Map.of(
            "식비",  "외식 횟수를 주 1회 줄이면 월 3만원 절약 가능해요.",
            "교통",  "대중교통을 이용하면 교통비를 절반으로 줄일 수 있어요.",
            "쇼핑",  "구매 전 48시간 대기 규칙으로 충동구매를 줄여보세요.",
            "카페",  "주 3회 커피를 집에서 내리면 월 2만원 절약 가능해요.",
            "편의점", "간식은 마트에서 대용량으로 구매하면 비용을 줄일 수 있어요.",
            "의료",  "정기 건강검진으로 큰 지출을 미리 예방해 보세요.",
            "문화",  "OTT 구독은 가족·친구와 공유하면 절약할 수 있어요.",
            "통신",  "알뜰폰으로 전환하면 월 통신비를 절반으로 줄일 수 있어요."
    );

    public SpendingReportResponse getSpendingReport(Long userId, Integer year, Integer month) {
        validate(year, month);

        LocalDate today = LocalDate.now();
        int y = year  != null ? year  : today.getYear();
        int m = month != null ? month : today.getMonthValue();

        LocalDate curStart  = LocalDate.of(y, m, 1);
        LocalDate prevStart = curStart.minusMonths(1);

        List<CategoryAmountRow> current = spendingMapper.findCategorySpending(
                userId, curStart.atStartOfDay(), curStart.plusMonths(1).atStartOfDay());
        List<CategoryAmountRow> prev = spendingMapper.findCategorySpending(
                userId, prevStart.atStartOfDay(), curStart.atStartOfDay());

        String period = String.format("%04d-%02d", y, m);

        if (current.isEmpty()) {
            return new SpendingReportResponse(period, "이번 달 소비 내역이 없습니다.", null, null);
        }

        String topCategory = current.get(0).getCategory();

        Map<String, BigDecimal> prevMap = prev.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CategoryAmountRow::getCategory, CategoryAmountRow::getAmount));

        // 전월 대비 증가액이 가장 큰 카테고리
        CategoryAmountRow mostIncreased = current.stream()
                .max(java.util.Comparator.comparing(r ->
                        r.getAmount().subtract(prevMap.getOrDefault(r.getCategory(), BigDecimal.ZERO))))
                .get();

        BigDecimal prevAmount = prevMap.getOrDefault(mostIncreased.getCategory(), BigDecimal.ZERO);
        String insight;
        if (prevAmount.compareTo(BigDecimal.ZERO) == 0) {
            insight = String.format("이번 달 %s 소비가 새롭게 시작되었습니다.", mostIncreased.getCategory());
        } else {
            BigDecimal change = mostIncreased.getAmount().subtract(prevAmount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevAmount, 0, RoundingMode.HALF_UP);
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                insight = String.format("이번 달 %s가 전월 대비 %s%% 증가했습니다.", mostIncreased.getCategory(), change);
            } else {
                insight = String.format("이번 달 모든 카테고리 소비가 전월보다 줄었습니다.");
            }
        }

        String savingTip = SAVING_TIPS.getOrDefault(topCategory, "소비 패턴을 점검해 보세요.");
        return new SpendingReportResponse(period, insight, topCategory, savingTip);
    }

    private void validate(Integer year, Integer month) {
        if (month != null && year == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "month 단독 입력은 허용되지 않습니다. year를 함께 입력해 주세요.");
        }
        try {
            if (year != null && month != null) {
                LocalDate.of(year, month, 1);
            } else if (year != null) {
                LocalDate.of(year, 1, 1);
            }
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효한 날짜를 입력해 주세요.");
        }
    }
}
