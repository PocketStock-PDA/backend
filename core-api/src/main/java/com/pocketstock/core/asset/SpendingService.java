package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.asset.dto.CategorySpending;
import com.pocketstock.core.asset.dto.SpendingResponse;
import com.pocketstock.core.asset.mapper.SpendingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private final SpendingMapper spendingMapper;

    public SpendingResponse getSpending(Long userId, Integer year, Integer month) {
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
}
