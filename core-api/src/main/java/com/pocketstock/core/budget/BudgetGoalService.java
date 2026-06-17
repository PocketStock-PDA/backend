package com.pocketstock.core.budget;

import com.pocketstock.core.budget.dto.AutoBudgetGoalResponse;
import com.pocketstock.core.budget.dto.BudgetGoalItem;
import com.pocketstock.core.budget.dto.CategorySpendingRow;
import com.pocketstock.core.budget.mapper.BudgetGoalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetGoalService {

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetGoalMapper budgetGoalMapper;

    @Transactional
    public AutoBudgetGoalResponse setAutoGoals(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        String currentPeriod = today.format(PERIOD_FMT);

        List<CategorySpendingRow> rows = budgetGoalMapper.findLastMonthCategorySpending(
                userId, lastMonth.getYear(), lastMonth.getMonthValue());

        List<BudgetGoalItem> categories = rows.stream()
                .map(row -> new BudgetGoalItem(row.getCategory(), roundUpToTenThousand(row.getTotalAmount())))
                .toList();

        long monthlyBudget = categories.stream().mapToLong(BudgetGoalItem::budget).sum();

        categories.forEach(item ->
                budgetGoalMapper.upsertCategoryGoal(
                        userId, currentPeriod, item.category(), BigDecimal.valueOf(item.budget())));

        return new AutoBudgetGoalResponse(monthlyBudget, categories);
    }

    private long roundUpToTenThousand(BigDecimal amount) {
        return amount.divide(TEN_THOUSAND, 0, RoundingMode.CEILING)
                .multiply(TEN_THOUSAND)
                .longValue();
    }
}
