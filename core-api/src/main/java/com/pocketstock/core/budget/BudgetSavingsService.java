package com.pocketstock.core.budget;

import com.pocketstock.core.budget.dto.BudgetGoalRow;
import com.pocketstock.core.budget.dto.BudgetSavingsRow;
import com.pocketstock.core.budget.dto.CategorySavingsItem;
import com.pocketstock.core.budget.dto.CategorySavingsResponse;
import com.pocketstock.core.budget.dto.CategorySavingsRow;
import com.pocketstock.core.budget.dto.CategorySpendingRow;
import com.pocketstock.core.budget.dto.ComparisonItem;
import com.pocketstock.core.budget.dto.ComparisonResponse;
import com.pocketstock.core.budget.dto.SavingsStatusResponse;
import com.pocketstock.core.budget.mapper.BudgetGoalMapper;
import com.pocketstock.core.budget.mapper.BudgetSavingsMapper;
import com.pocketstock.core.notification.NotificationService;
import com.pocketstock.core.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetSavingsService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetSavingsMapper savingsMapper;
    private final BudgetGoalMapper goalMapper;
    private final NotificationService notificationService;

    public CategorySavingsResponse getCategoryWithSavings(Long userId) {
        LocalDate today = LocalDate.now();
        String period = today.format(PERIOD_FMT);
        LocalDateTime from = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        List<CategorySavingsRow> rows = savingsMapper.findCategoryGoalsWithSpending(userId, period, from, to);

        List<CategorySavingsItem> categories = rows.stream()
                .map(row -> {
                    BigDecimal saved = row.getTargetAmount().subtract(row.getSpentAmount())
                            .max(BigDecimal.ZERO);
                    BigDecimal rate = row.getTargetAmount().compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : row.getSpentAmount().divide(row.getTargetAmount(), 4, RoundingMode.HALF_UP);
                    return new CategorySavingsItem(
                            row.getCategory(), row.getTargetAmount(), row.getSpentAmount(), saved, rate);
                })
                .toList();

        BigDecimal totalBudget = categories.stream()
                .map(CategorySavingsItem::targetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = categories.stream()
                .map(CategorySavingsItem::spentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaved = categories.stream()
                .map(CategorySavingsItem::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CategorySavingsResponse(period, totalBudget, totalSpent, totalSaved, categories);
    }

    public ComparisonResponse getComparison(Long userId) {
        LocalDate today = LocalDate.now();
        String currentPeriod = today.format(PERIOD_FMT);
        String lastPeriod = today.minusMonths(1).format(PERIOD_FMT);

        LocalDateTime currentFrom = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime currentTo = currentFrom.plusMonths(1);
        LocalDateTime lastFrom = currentFrom.minusMonths(1);
        LocalDateTime lastTo = currentFrom;

        Map<String, BigDecimal> currentMap = goalMapper
                .findLastMonthCategorySpending(userId, currentFrom, currentTo).stream()
                .collect(Collectors.toMap(CategorySpendingRow::getCategory, CategorySpendingRow::getTotalAmount,
                        BigDecimal::add));
        Map<String, BigDecimal> lastMap = goalMapper
                .findLastMonthCategorySpending(userId, lastFrom, lastTo).stream()
                .collect(Collectors.toMap(CategorySpendingRow::getCategory, CategorySpendingRow::getTotalAmount,
                        BigDecimal::add));

        Set<String> allCategories = new LinkedHashSet<>(lastMap.keySet());
        allCategories.addAll(currentMap.keySet());

        List<ComparisonItem> items = allCategories.stream()
                .map(cat -> {
                    BigDecimal current = currentMap.getOrDefault(cat, BigDecimal.ZERO);
                    BigDecimal last = lastMap.getOrDefault(cat, BigDecimal.ZERO);
                    BigDecimal change = current.subtract(last);
                    BigDecimal rate = last.compareTo(BigDecimal.ZERO) == 0 ? null
                            : change.divide(last, 4, RoundingMode.HALF_UP);
                    return new ComparisonItem(cat, current, last, change, rate);
                })
                .toList();

        return new ComparisonResponse(currentPeriod, lastPeriod, items);
    }

    public SavingsStatusResponse getSavingsStatus(Long userId) {
        LocalDate today = LocalDate.now();
        String period = today.format(PERIOD_FMT);
        LocalDateTime from = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        List<BudgetGoalRow> goals = goalMapper.findGoalsByPeriod(userId, period);
        BigDecimal totalBudget = goals.stream()
                .map(BudgetGoalRow::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = goalMapper.findLastMonthCategorySpending(userId, from, to).stream()
                .map(CategorySpendingRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal savedAmount = totalBudget.subtract(totalSpent).max(BigDecimal.ZERO);

        BudgetSavingsRow row = savingsMapper.findBudgetSavings(userId, period);
        BigDecimal targetSavingsAmount = row != null ? row.getTargetAmount() : BigDecimal.ZERO;
        boolean isCollectAgreed = row != null && row.isCollectAgreed();
        String transferStatus = row != null ? row.getTransferStatus() : "PENDING";

        return new SavingsStatusResponse(
                period, totalBudget, totalSpent, savedAmount,
                targetSavingsAmount, isCollectAgreed, transferStatus);
    }

    @Transactional
    public void agreeCollect(Long userId) {
        String period = LocalDate.now().format(PERIOD_FMT);
        savingsMapper.agreeCollect(userId, period);
        notificationService.create(userId, NotificationType.GOAL_NUDGE,
                "절약금 모으기 동의", "이번 달 절약금을 CMA 계좌로 이체하기로 동의했어요.");
    }
}
