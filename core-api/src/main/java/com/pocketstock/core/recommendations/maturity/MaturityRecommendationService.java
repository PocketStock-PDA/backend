package com.pocketstock.core.recommendations.maturity;

import com.pocketstock.core.recommendations.maturity.dto.DividendStockItem;
import com.pocketstock.core.recommendations.maturity.dto.DividendStockRow;
import com.pocketstock.core.recommendations.maturity.dto.MaturityRecommendationResponse;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountDto;
import com.pocketstock.core.recommendations.maturity.dto.TriggerAccountRow;
import com.pocketstock.core.recommendations.maturity.mapper.MaturityRecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaturityRecommendationService {

    private final MaturityRecommendationMapper mapper;

    public MaturityRecommendationResponse recommend(Long userId) {
        TriggerAccountRow account = mapper.findUpcomingMaturityAccount(userId);
        if (account == null) return null;

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
                        r.getDividendYield(),
                        parseTags(r.getTags()),
                        r.getExDividendDate(),
                        reason
                ))
                .toList();

        TriggerAccountDto triggerAccount = new TriggerAccountDto(
                account.getAccountName(),
                account.getMaturityDate(),
                account.getMaturityAmount(),
                account.getDaysUntilMaturity(),
                interestRatePct
        );

        return new MaturityRecommendationResponse(triggerAccount, items);
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.asList(tags.split("\\|"));
    }
}
