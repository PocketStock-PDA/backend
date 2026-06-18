package com.pocketstock.core.budget;

import com.pocketstock.core.budget.dto.AutoBudgetGoalResponse;
import com.pocketstock.core.budget.dto.BudgetGoalCategoryItem;
import com.pocketstock.core.budget.dto.BudgetGoalItem;
import com.pocketstock.core.budget.dto.BudgetGoalRequest;
import com.pocketstock.core.budget.dto.BudgetGoalRow;
import com.pocketstock.core.budget.dto.BudgetGoalSummary;
import com.pocketstock.core.budget.dto.CalendarDayItem;
import com.pocketstock.core.budget.dto.CalendarResponse;
import com.pocketstock.core.budget.dto.CategorySpendingRow;
import com.pocketstock.core.budget.dto.DailySpendingRow;
import com.pocketstock.core.budget.mapper.BudgetGoalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetGoalService {

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetGoalMapper budgetGoalMapper;

    @Transactional
    public AutoBudgetGoalResponse setAutoGoals(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        String currentPeriod = today.format(PERIOD_FMT);

        LocalDateTime from = firstOfLastMonth.atStartOfDay();
        LocalDateTime to   = firstOfLastMonth.plusMonths(1).atStartOfDay();

        List<CategorySpendingRow> rows = budgetGoalMapper.findLastMonthCategorySpending(userId, from, to);

        List<BudgetGoalItem> categories = rows.stream()
                .map(row -> new BudgetGoalItem(row.getCategory(), roundUpToTenThousand(row.getTotalAmount())))
                .toList();

        BigDecimal monthlyBudget = categories.stream()
                .map(BudgetGoalItem::budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        categories.forEach(item ->
                budgetGoalMapper.upsertCategoryGoal(userId, currentPeriod, item.category(), item.budget()));

        return new AutoBudgetGoalResponse(monthlyBudget, categories);
    }

    @Transactional
    public AutoBudgetGoalResponse setManualGoals(Long userId, BudgetGoalRequest request) {
        String currentPeriod = LocalDate.now().format(PERIOD_FMT);

        List<BudgetGoalItem> categories = request.categories();

        BigDecimal monthlyBudget = categories.stream()
                .map(BudgetGoalItem::budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        categories.forEach(item ->
                budgetGoalMapper.upsertCategoryGoal(userId, currentPeriod, item.category(), item.budget()));

        return new AutoBudgetGoalResponse(monthlyBudget, categories);
    }

    public BudgetGoalSummary getGoals(Long userId) {
        LocalDate today = LocalDate.now();
        String currentPeriod = today.format(PERIOD_FMT);

        List<BudgetGoalRow> goalRows = budgetGoalMapper.findGoalsByPeriod(userId, currentPeriod);

        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDateTime from = firstOfMonth.atStartOfDay();
        LocalDateTime to   = firstOfMonth.plusMonths(1).atStartOfDay();

        Map<String, BigDecimal> spendingMap = budgetGoalMapper
                .findLastMonthCategorySpending(userId, from, to).stream()
                .collect(Collectors.toMap(CategorySpendingRow::getCategory, CategorySpendingRow::getTotalAmount));

        List<BudgetGoalCategoryItem> categories = goalRows.stream()
                .map(row -> new BudgetGoalCategoryItem(
                        row.getCategory(),
                        row.getTargetAmount(),
                        spendingMap.getOrDefault(row.getCategory(), BigDecimal.ZERO)))
                .toList();

        BigDecimal monthlyBudget = categories.stream()
                .map(BudgetGoalCategoryItem::budget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal spentAmount = categories.stream()
                .map(BudgetGoalCategoryItem::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BudgetGoalSummary(monthlyBudget, spentAmount, monthlyBudget.subtract(spentAmount), categories);
    }

    public CalendarResponse getCalendar(Long userId, int year, int month) {
        YearMonth ym       = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to   = ym.plusMonths(1).atDay(1).atStartOfDay();
        String period      = ym.format(PERIOD_FMT);

        // 월 예산 합계
        List<BudgetGoalRow> goals = budgetGoalMapper.findGoalsByPeriod(userId, period);
        BigDecimal monthlyBudget  = goals.stream()
                .map(BudgetGoalRow::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int daysInMonth       = ym.lengthOfMonth();
        BigDecimal dailyBudget = monthlyBudget.divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.DOWN);

        // 일별 지출 Map (date → spent)
        Map<String, BigDecimal> spendingMap = budgetGoalMapper.findDailySpending(userId, from, to)
                .stream()
                .collect(Collectors.toMap(DailySpendingRow::getDate, DailySpendingRow::getSpent));

        // 오늘까지만 표시 (미래 날짜 제외)
        LocalDate today   = LocalDate.now();
        LocalDate lastDay = ym.atEndOfMonth().isBefore(today) ? ym.atEndOfMonth() : today;

        List<CalendarDayItem> days = new ArrayList<>();
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = ym.atDay(d);
            if (date.isAfter(lastDay)) break;
            String dateStr      = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            BigDecimal spent    = spendingMap.getOrDefault(dateStr, BigDecimal.ZERO);
            String status       = spent.compareTo(dailyBudget) <= 0 ? "SAFE" : "OVER";
            days.add(new CalendarDayItem(dateStr, spent, status));
        }

        return new CalendarResponse(year, month, dailyBudget, days);
    }

    private BigDecimal roundUpToTenThousand(BigDecimal amount) {
        return amount.divide(TEN_THOUSAND, 0, RoundingMode.CEILING)
                .multiply(TEN_THOUSAND);
    }
}
