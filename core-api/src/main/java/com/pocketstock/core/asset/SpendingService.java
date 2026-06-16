package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.asset.dto.CategorySpending;
import com.pocketstock.core.asset.dto.SpendingResponse;
import com.pocketstock.core.asset.mapper.SpendingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private final SpendingMapper spendingMapper;

    public SpendingResponse getSpending(Long userId, Integer year, Integer month) {
        List<CategoryAmountRow> rows = spendingMapper.findCategorySpending(userId, year, month);

        long total = rows.stream().mapToLong(CategoryAmountRow::getAmount).sum();

        List<CategorySpending> categories = rows.stream()
                .map(r -> new CategorySpending(
                        r.getCategory(),
                        r.getAmount(),
                        total == 0 ? 0.0 : Math.round(r.getAmount() * 1000.0 / total) / 10.0
                ))
                .toList();

        return new SpendingResponse(total, categories);
    }
}
