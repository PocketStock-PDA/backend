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
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BudgetTransactionService {

    private static final Set<String> VALID_TYPES = Set.of("DAILY", "MONTHLY");

    private final BudgetTransactionMapper budgetTransactionMapper;

    public TransactionsResponse getTransactions(Long userId, String type, Integer year, Integer month, Integer day) {
        validate(type, year, month, day);

        List<TransactionRow> rows = budgetTransactionMapper.findTransactions(userId, year, month, day);

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
    }
}
