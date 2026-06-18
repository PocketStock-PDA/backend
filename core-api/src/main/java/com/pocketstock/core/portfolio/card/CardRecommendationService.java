package com.pocketstock.core.portfolio.card;

import com.pocketstock.core.asset.dto.CategoryAmountRow;
import com.pocketstock.core.portfolio.card.dto.CardBenefitRow;
import com.pocketstock.core.portfolio.card.dto.CardRecommendationItem;
import com.pocketstock.core.portfolio.card.dto.CardRow;
import com.pocketstock.core.portfolio.card.mapper.CardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardRecommendationService {

    private final CardMapper cardMapper;

    // 거래 카테고리 → benefit_category 키워드 매핑
    private static final Map<String, List<String>> CATEGORY_MAP = Map.ofEntries(
        Map.entry("교통", List.of("대중교통", "버스", "지하철", "택시", "KTX", "고속버스", "하이패스", "K-패스", "교통/통신", "교통·페이퍼리스", "출퇴근 통행료")),
        Map.entry("음식·숙박", List.of("음식점", "카페", "배달", "외식", "편의점", "커피", "제과", "밀딜리버리", "배민", "주말 외식비", "One Pick 맛집")),
        Map.entry("식료품·비주류음료", List.of("마트", "편의점", "장보기", "CU", "GS리테일", "할인점", "생활밀착업종")),
        Map.entry("보건", List.of("병원", "약국", "의료", "피트니스", "올리브영")),
        Map.entry("오락·문화", List.of("OTT", "멤버십", "영화", "테마파크", "스터디", "디지털 구독")),
        Map.entry("정보통신", List.of("통신", "이동통신", "알뜰폰", "공과금", "구독", "월납요금", "정기결제")),
        Map.entry("의류·신발", List.of("쇼핑", "백화점", "신세계", "온라인", "One Pick 쇼핑몰", "생활/쇼핑")),
        Map.entry("교육", List.of("학원", "인터넷서점", "교육업종")),
        Map.entry("주거·수도·광열", List.of("아파트관리비", "공과금", "관리비", "주거")),
        Map.entry("가정용품·가사서비스", List.of("생활 가맹점", "생활서비스", "생활 부문", "일상생활비")),
        Map.entry("주류·담배", List.of("편의점", "마트")),
        Map.entry("기타상품·서비스", List.of("전 가맹점", "전가맹점", "국내 이용", "생활 가맹점"))
    );

    private static final int TOP_N = 5;

    public List<CardRecommendationItem> recommend(Long userId) {
        // 최근 3개월 소비 카테고리별 합계
        LocalDateTime from = LocalDate.now().minusMonths(3).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        List<CategoryAmountRow> spending = cardMapper.findCategorySpending(userId, from, to);

        BigDecimal total = spending.stream()
                .map(CategoryAmountRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        // 카테고리별 소비 비중 (0~100)
        Map<String, Double> spendRatio = spending.stream().collect(Collectors.toMap(
                CategoryAmountRow::getCategory,
                r -> r.getAmount().multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, java.math.RoundingMode.HALF_UP)
                        .doubleValue()
        ));

        // 카드별 혜택 그룹핑
        List<CardRow> cards = cardMapper.findAllActiveCards();
        Map<Long, List<CardBenefitRow>> benefitsByCard = cardMapper.findAllBenefits().stream()
                .collect(Collectors.groupingBy(CardBenefitRow::getCardId));

        // 각 카드 matchRate 계산
        List<CardRecommendationItem> result = cards.stream().map(card -> {
            List<CardBenefitRow> benefits = benefitsByCard.getOrDefault(card.getId(), List.of());
            Set<String> benefitKeywords = benefits.stream()
                    .map(CardBenefitRow::getBenefitCategory)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            double matchRate = spendRatio.entrySet().stream()
                    .filter(e -> {
                        List<String> keywords = CATEGORY_MAP.getOrDefault(e.getKey(), List.of());
                        return keywords.stream().anyMatch(k ->
                                benefitKeywords.stream().anyMatch(b -> b.contains(k.toLowerCase()))
                        );
                    })
                    .mapToDouble(Map.Entry::getValue)
                    .sum();

            // 매칭된 혜택 설명 (상위 3개)
            List<String> matchedBenefits = benefits.stream()
                    .filter(b -> {
                        String bc = b.getBenefitCategory().toLowerCase();
                        return spendRatio.keySet().stream().anyMatch(cat ->
                                CATEGORY_MAP.getOrDefault(cat, List.of()).stream()
                                        .anyMatch(k -> bc.contains(k.toLowerCase()))
                        );
                    })
                    .map(CardBenefitRow::getBenefitDesc)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(3)
                    .toList();

            return new CardRecommendationItem(
                    card.getCardName(),
                    card.getProvider(),
                    card.getAnnualFeeDomestic(),
                    matchedBenefits,
                    (int) Math.min(Math.round(matchRate), 100)
            );
        })
        .filter(item -> item.matchRate() > 0)
        .sorted(Comparator.comparingInt(CardRecommendationItem::matchRate).reversed())
        .limit(TOP_N)
        .toList();

        return result;
    }
}
