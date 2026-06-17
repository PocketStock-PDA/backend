package com.pocketstock.core.budget;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.budget.dto.TransactionItem;
import com.pocketstock.core.budget.dto.TransactionRow;
import com.pocketstock.core.budget.dto.TransactionsResponse;
import com.pocketstock.core.budget.mapper.BudgetTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BudgetTransactionService {

    private static final Set<String> VALID_TYPES = Set.of("DAILY", "MONTHLY");

    private final BudgetTransactionMapper budgetTransactionMapper;

    public TransactionsResponse getTransactions(Long userId, String type, Integer year, Integer month, Integer day) {
        validate(type, year, month, day);

        LocalDateTime from = null;
        LocalDateTime to   = null;

        if ("MONTHLY".equals(type)) {
            LocalDate start = LocalDate.of(year, month, 1);
            from = start.atStartOfDay();
            to   = start.plusMonths(1).atStartOfDay();
        } else if ("DAILY".equals(type)) {
            LocalDate date = LocalDate.of(year, month, day);
            from = date.atStartOfDay();
            to   = date.plusDays(1).atStartOfDay();
        }

        List<TransactionRow> rows = budgetTransactionMapper.findTransactions(userId, from, to);

        List<TransactionItem> transactions = rows.stream()
                .map(r -> new TransactionItem(
                        r.getTransactionId(),
                        r.getCategory(),
                        r.getDescription(),
                        r.getAmount(),
                        r.getTransactedAt()
                ))
                .toList();

        BigDecimal totalAmount = rows.stream()
                .map(TransactionRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TransactionsResponse(transactions, totalAmount);
    }

    private void validate(String type, Integer year, Integer month, Integer day) {
        if (type == null) {
            return;
        }
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type은 DAILY 또는 MONTHLY만 가능합니다.");
        }
        if (type.equals("DAILY") && (year == null || month == null || day == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "DAILY 조회 시 year, month, day가 모두 필요합니다.");
        }
        if (type.equals("MONTHLY") && (year == null || month == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "MONTHLY 조회 시 year, month가 필요합니다.");
        }
        try {
            if ("MONTHLY".equals(type)) {
                LocalDate.of(year, month, 1);
            } else if ("DAILY".equals(type)) {
                LocalDate.of(year, month, day);
            }
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효한 날짜를 입력해 주세요.");
        }
    }
}
