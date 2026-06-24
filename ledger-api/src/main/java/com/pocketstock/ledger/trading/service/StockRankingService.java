package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.kis.KisOverseasRankResponse;
import com.pocketstock.ledger.kis.KisRankingClient;
import com.pocketstock.ledger.ls.LsRankingClient;
import com.pocketstock.ledger.ls.LsT1463Response;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.RankingSort;
import com.pocketstock.ledger.trading.dto.StockRankingItem;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 종목 순위 — 브로커 순위(국내 LS t1463)를 받아 자체 종목마스터(tradable_stocks) 교집합만 남기고 재랭킹한다.
 * 우리 서비스 범위(코스피200·코스닥150·미국 ~600)는 시장 전체 순위에 다 들지 못할 수 있어 "전체순위 ∩ 유니버스" 의미.
 *
 * <p>국내(LS t1463): 한 응답에 거래대금(value)·시가총액(total)이 함께 와 두 정렬을 한 소스로 처리.
 * 단, t1463은 거래대금순 페이징이라 시총 정렬은 받은 윈도우(상위 거래대금) 내 재정렬 — 유동성 높은 유니버스 특성상
 * 시총 상위 종목은 윈도우에 포함되므로 실용상 충분. 정밀 시총순위(t1444)는 후속 고려.
 * <p>해외(KIS): 거래대금(trade-pbmn)·시총(market-cap) TR이 갈려 정렬에 맞는 TR을 거래소별(NAS/NYS)로 호출→머지.
 * 우리 유니버스 개별주는 NAS/NYS에 분포(AMEX는 사실상 ETF라 제외), ETF는 sec_type로 필터. 값 단위는 USD.
 */
@Service
@RequiredArgsConstructor
public class StockRankingService {

    /** 순위 노출 개수 — 고정 30(국내·해외 공통). */
    private static final int RANK_SIZE = 30;
    /** LS에서 끌어올 원시 행 수 — 유니버스 필터·시총 재정렬 여유분 포함. */
    private static final int FETCH_WINDOW = 100;
    /** 단위 환산: value(백만원)→원, total(억원)→원. */
    private static final BigDecimal VALUE_TO_KRW = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal TOTAL_TO_KRW = BigDecimal.valueOf(100_000_000L);
    /**
     * 해외 개별주 분포 거래소(AMEX는 사실상 ETF라 스킵). KIS EXCD 코드 — 정규장 코드만.
     * ※ 순위 TR은 정규장 EXCD(NAS/NYS/AMS)만 지원 — KIS 문서상 주간거래 코드(BAQ/BAY/BAA) 미허용.
     *   주간장(KST 10~16) 중엔 NAS가 직전 정규장 마감 기준 순위를 반환. 현재가/호가의 세션 분기(OverseasExchangeCode)와 달리 순위는 세션 무관.
     */
    private static final String[] OVERSEAS_EXCDS = {"NAS", "NYS"};
    private static final String SEC_TYPE_STOCK = "STOCK";

    private final LsRankingClient lsRankingClient;
    private final KisRankingClient kisRankingClient;
    private final StockMapper stockMapper;

    @Transactional(readOnly = true)
    public List<StockRankingItem> getDomesticRanking(Long userId, String sort) {
        requireAuth(userId);
        return domesticRanking(RankingSort.fromParam(sort), RANK_SIZE);
    }

    @Transactional(readOnly = true)
    public List<StockRankingItem> getOverseasRanking(Long userId, String sort) {
        requireAuth(userId);
        return overseasRanking(RankingSort.fromParam(sort), RANK_SIZE);
    }

    /** 국내 순위 — LS t1463 윈도우 → 유니버스 필터 → 정렬 → 재랭킹. */
    private List<StockRankingItem> domesticRanking(RankingSort sort, int size) {
        List<LsT1463Response.Item> rows = lsRankingClient.getDomesticTradeValueRanking(FETCH_WINDOW);

        // 코드별 첫 행만(거래대금 상위 우선) — 페이지 경계 중복 안전
        LinkedHashMap<String, LsT1463Response.Item> byCode = new LinkedHashMap<>();
        for (LsT1463Response.Item r : rows) {
            if (r.shcode() != null && !r.shcode().isBlank()) {
                byCode.putIfAbsent(r.shcode(), r);
            }
        }
        if (byCode.isEmpty()) {
            return List.of();
        }

        // 자체 종목마스터(활성)에 존재하는 것만 — exchange/명/통화/로고는 마스터 기준
        Map<String, TradableStock> universe = stockMapper.findByCodes(new ArrayList<>(byCode.keySet())).stream()
                .collect(Collectors.toMap(TradableStock::getStockCode, s -> s, (a, b) -> a));

        Comparator<LsT1463Response.Item> cmp = switch (sort) {
            case TRADE_VALUE -> Comparator.comparing((LsT1463Response.Item r) -> nz(r.value())).reversed();
            case MARKET_CAP -> Comparator.comparing((LsT1463Response.Item r) -> nz(r.total())).reversed();
        };

        List<LsT1463Response.Item> matched = byCode.values().stream()
                .filter(r -> universe.containsKey(r.shcode()))
                .sorted(cmp)
                .toList();

        List<StockRankingItem> out = new ArrayList<>();
        int rank = 1;
        for (LsT1463Response.Item r : matched) {
            if (out.size() >= size) {
                break;
            }
            TradableStock s = universe.get(r.shcode());
            out.add(new StockRankingItem(
                    rank++,
                    s.getStockCode(),
                    s.getStockName(),
                    s.getExchange(),
                    s.getCurrency(),
                    r.price(),
                    nz(r.diff()),
                    nz(r.value()).multiply(VALUE_TO_KRW),
                    nz(r.total()).multiply(TOTAL_TO_KRW),
                    s.getLogoUrl()));
        }
        return out;
    }

    /** 해외 순위 — 정렬에 맞는 KIS TR을 거래소별 호출→머지 → 유니버스(개별주)만 → 정렬 → 재랭킹. 값 단위 USD. */
    private List<StockRankingItem> overseasRanking(RankingSort sort, int size) {
        // 거래소별 호출 후 symb 기준 머지(중복 시 첫 행 유지)
        LinkedHashMap<String, KisOverseasRankResponse.Item> bySymb = new LinkedHashMap<>();
        for (String excd : OVERSEAS_EXCDS) {
            List<KisOverseasRankResponse.Item> rows = (sort == RankingSort.TRADE_VALUE)
                    ? kisRankingClient.getOverseasTradeAmountRank(excd)
                    : kisRankingClient.getOverseasMarketCapRank(excd);
            for (KisOverseasRankResponse.Item r : rows) {
                if (r.symb() != null && !r.symb().isBlank()) {
                    bySymb.putIfAbsent(r.symb(), r);
                }
            }
        }
        if (bySymb.isEmpty()) {
            return List.of();
        }

        // 자체 종목마스터(활성)에 존재하는 개별주(STOCK)만 — KIS엔 ETF 제외 파라미터가 없어 sec_type로 거른다
        Map<String, TradableStock> universe = stockMapper.findByCodes(new ArrayList<>(bySymb.keySet())).stream()
                .filter(s -> SEC_TYPE_STOCK.equals(s.getSecType()))
                .collect(Collectors.toMap(TradableStock::getStockCode, s -> s, (a, b) -> a));

        Comparator<KisOverseasRankResponse.Item> cmp = switch (sort) {
            case TRADE_VALUE -> Comparator.comparing((KisOverseasRankResponse.Item r) -> parse(r.tradeAmount())).reversed();
            case MARKET_CAP -> Comparator.comparing((KisOverseasRankResponse.Item r) -> parse(r.marketCap())).reversed();
        };

        List<KisOverseasRankResponse.Item> matched = bySymb.values().stream()
                .filter(r -> universe.containsKey(r.symb()))
                .sorted(cmp)
                .toList();

        List<StockRankingItem> out = new ArrayList<>();
        int rank = 1;
        for (KisOverseasRankResponse.Item r : matched) {
            if (out.size() >= size) {
                break;
            }
            TradableStock s = universe.get(r.symb());
            out.add(new StockRankingItem(
                    rank++,
                    s.getStockCode(),
                    s.getStockName(),
                    s.getExchange(),
                    s.getCurrency(),
                    parseOrNull(r.price()),
                    parseOrNull(r.rate()),
                    parseOrNull(r.tradeAmount()),  // USD — 시총정렬 TR이면 null
                    parseOrNull(r.marketCap()),    // USD — 거래대금정렬 TR이면 null
                    s.getLogoUrl()));
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    /** 정렬키용 — 파싱 실패/공란은 0(정렬에서 최하위로). */
    private static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** 표시값용 — 파싱 실패/공란은 null(해당 TR이 안 주는 지표는 비움). */
    private static BigDecimal parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
