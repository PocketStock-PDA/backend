package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.asset.dto.CategorySpending;
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
