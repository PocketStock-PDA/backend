package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.AutoInvestSetting;
import com.pocketstock.ledger.trading.domain.AutoInvestStock;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.AutoInvestOverviewResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestRequest;
import com.pocketstock.ledger.trading.dto.AutoInvestResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestSettingMapper;
import com.pocketstock.ledger.trading.mapper.AutoInvestStockMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 자동모으기 설정 CRUD (주기 base). 등록 시 종목→거래소→시장·통화·계좌를 해석하고, 주기·금액 규칙을 검증한다.
 * 실제 매수는 AutoInvestScheduler가 place(source=AUTO)로 집행(이 서비스는 설정만 관리).
 * 트리거(물타기/익절)는 별도 서비스/엔드포인트(레이어 구조).
 */
@Service
@RequiredArgsConstructor
public class AutoInvestService {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> OVERSEAS_EXCHANGES = Set.of("NASDAQ", "NYSE", "AMEX");
    private static final String MARKET_DOMESTIC = "DOMESTIC";
    private static final String MARKET_OVERSEAS = "OVERSEAS";
    private static final String CURRENCY_KRW = "KRW";
    private static final String CURRENCY_USD = "USD";
    private static final BigDecimal MIN_ORDER_KRW = BigDecimal.valueOf(1000);
    private static final BigDecimal KRW_UNIT = BigDecimal.valueOf(1000);
    private static final BigDecimal MIN_ORDER_USD = new BigDecimal("0.01");
    private static final Set<String> PERIODS = Set.of("DAILY", "WEEKLY", "MONTHLY");

    private final AutoInvestSettingMapper settingMapper;
    private final AutoInvestStockMapper stockMapper;
    private final StockMapper stockMasterMapper;
    private final SecuritiesAccountMapper accountMapper;

    /** 종목 등록 — 종목·계좌 해석 + 검증 후 1행 INSERT. 같은 종목 중복 시 409. */
    @Transactional
    public AutoInvestResponse register(Long userId, AutoInvestRequest req) {
        requireUser(userId);
        TradableStock stock = resolveStock(req.stockCode());
        Resolved r = resolveMarket(stock);
        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, r.market());
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    (r.overseas() ? "해외" : "국내") + " 위탁계좌가 없습니다. 먼저 계좌를 개설하세요.");
        }
        String period = normalize(req.period());
        Integer periodDay = validatePeriod(period, req.periodDay());
        String amountType = validateAmount(req, r.currency());

        ensureSettings(userId);
        AutoInvestStock entity = AutoInvestStock.builder()
                .userId(userId)
                .accountId(account.getId())
                .stockCode(stock.getStockCode())
                .market(r.market())
                .period(period)
                .periodDay(periodDay)
                .amountType(amountType)
                .buyAmount("AMOUNT".equals(amountType) ? req.buyAmount() : null)
                .buyQuantity("QUANTITY".equals(amountType) ? req.buyQuantity() : null)
                .currency(r.currency())
                .isActive(true)
                .build();
        try {
            stockMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 자동모으기 중인 종목입니다.");
        }
        entity.setStockName(stock.getStockName());
        return AutoInvestResponse.from(entity);
    }

    /** 종합 조회 — 전역 스위치 + 종목 목록. */
    @Transactional(readOnly = true)
    public AutoInvestOverviewResponse getOverview(Long userId) {
        requireUser(userId);
        AutoInvestSetting s = settingMapper.findByUserId(userId);
        List<AutoInvestResponse> stocks = stockMapper.findByUserId(userId).stream()
                .map(AutoInvestResponse::from)
                .toList();
        boolean enabled = s != null && Boolean.TRUE.equals(s.getIsEnabled());
        boolean paused = s != null && Boolean.TRUE.equals(s.getIsPaused());
        boolean keep = s == null || Boolean.TRUE.equals(s.getKeepCollectingOnPause());
        return new AutoInvestOverviewResponse(enabled, paused, keep, stocks);
    }

    /** 단건 상세. */
    @Transactional(readOnly = true)
    public AutoInvestResponse getOne(Long userId, Long id) {
        requireUser(userId);
        AutoInvestStock s = stockMapper.findByIdAndUserId(id, userId);
        if (s == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        return AutoInvestResponse.from(s);
    }

    /** 설정 수정(주기·금액). 종목/계좌/통화는 불변. */
    @Transactional
    public AutoInvestResponse update(Long userId, Long id, AutoInvestRequest req) {
        requireUser(userId);
        AutoInvestStock existing = stockMapper.findByIdAndUserId(id, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        String period = normalize(req.period());
        Integer periodDay = validatePeriod(period, req.periodDay());
        String amountType = validateAmount(req, existing.getCurrency());

        existing.setPeriod(period);
        existing.setPeriodDay(periodDay);
        existing.setAmountType(amountType);
        existing.setBuyAmount("AMOUNT".equals(amountType) ? req.buyAmount() : null);
        existing.setBuyQuantity("QUANTITY".equals(amountType) ? req.buyQuantity() : null);
        stockMapper.update(existing);
        return AutoInvestResponse.from(existing);
    }

    /** 일시중지/재개 — action=PAUSE/RESUME. */
    @Transactional
    public void updateStatus(Long userId, Long id, String action) {
        requireUser(userId);
        String a = normalize(action);
        boolean active;
        if ("RESUME".equals(a)) {
            active = true;
        } else if ("PAUSE".equals(a)) {
            active = false;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "action은 PAUSE 또는 RESUME이어야 합니다.");
        }
        if (stockMapper.updateActive(id, userId, active) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
    }

    /** 해제(완전 삭제) — 트리거·회차로그는 FK CASCADE로 함께 삭제. */
    @Transactional
    public void remove(Long userId, Long id) {
        requireUser(userId);
        if (stockMapper.deleteByIdAndUserId(id, userId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
    }

    // ---- 내부 ----

    private void ensureSettings(Long userId) {
        if (settingMapper.findByUserId(userId) == null) {
            settingMapper.insert(AutoInvestSetting.builder()
                    .userId(userId).isEnabled(true).isPaused(false).keepCollectingOnPause(true).build());
        }
    }

    private TradableStock resolveStock(String stockCode) {
        TradableStock stock = stockMasterMapper.findByCode(stockCode);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        if (Boolean.FALSE.equals(stock.getIsActive())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "거래 정지 종목입니다: " + stockCode);
        }
        return stock;
    }

    private Resolved resolveMarket(TradableStock stock) {
        if (DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            return new Resolved(MARKET_DOMESTIC, CURRENCY_KRW, false);
        }
        if (OVERSEAS_EXCHANGES.contains(stock.getExchange())) {
            return new Resolved(MARKET_OVERSEAS, CURRENCY_USD, true);
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 거래소: " + stock.getExchange());
    }

    /** 주기 검증 + 정규화된 periodDay 반환(DAILY는 null). */
    private Integer validatePeriod(String period, Integer day) {
        if (!PERIODS.contains(period)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주기(period)는 DAILY/WEEKLY/MONTHLY 중 하나여야 합니다.");
        }
        if ("DAILY".equals(period)) {
            return null;
        }
        if ("WEEKLY".equals(period)) {
            if (day == null || day < 1 || day > 5) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "주1회는 요일(월~금, 1~5)을 지정해야 합니다.");
            }
            return day;
        }
        // MONTHLY
        if (day == null || day < 1 || day > 31) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월1회는 날짜(1~31)를 지정해야 합니다.");
        }
        return day;
    }

    /** 금액/수량 검증 + 정규화된 amountType 반환. */
    private String validateAmount(AutoInvestRequest req, String currency) {
        String amountType = normalize(req.amountType());
        if ("AMOUNT".equals(amountType)) {
            BigDecimal amount = req.buyAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매수 금액(buyAmount)을 입력해주세요.");
            }
            boolean domestic = CURRENCY_KRW.equals(currency);
            BigDecimal min = domestic ? MIN_ORDER_KRW : MIN_ORDER_USD;
            if (amount.compareTo(min) < 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "최소 매수금액은 " + (domestic ? "1,000원" : "$0.01") + "입니다.");
            }
            if (domestic && amount.remainder(KRW_UNIT).signum() != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매수금액은 1,000원 단위입니다.");
            }
            return amountType;
        }
        if ("QUANTITY".equals(amountType)) {
            if (req.buyQuantity() == null || req.buyQuantity().signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "매수 수량(buyQuantity)을 입력해주세요.");
            }
            return amountType;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "amountType은 AMOUNT 또는 QUANTITY여야 합니다.");
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private record Resolved(String market, String currency, boolean overseas) {
    }
}
