package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.AutoInvestSetting;
import com.pocketstock.ledger.trading.domain.AutoInvestStock;
import com.pocketstock.ledger.trading.domain.AutoInvestTrigger;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.AutoInvestExecutionResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestOverviewResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestRequest;
import com.pocketstock.ledger.trading.dto.AutoInvestResponse;
import com.pocketstock.ledger.trading.dto.AutoInvestTriggerRequest;
import com.pocketstock.ledger.trading.dto.AutoInvestTriggerResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestExecutionMapper;
import com.pocketstock.ledger.trading.mapper.AutoInvestSettingMapper;
import com.pocketstock.ledger.trading.mapper.AutoInvestStockMapper;
import com.pocketstock.ledger.trading.mapper.AutoInvestTriggerMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import com.pocketstock.ledger.trading.matching.TriggerArmedEvent;
import com.pocketstock.ledger.trading.matching.TriggerDisarmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final BigDecimal MIN_ORDER_USD = new BigDecimal("1");
    private static final Set<String> PERIODS = Set.of("DAILY", "WEEKLY", "MONTHLY");

    private static final Set<String> BUY_ACTIONS = Set.of("AMOUNT", "QUANTITY");
    private static final Set<String> SELL_ACTIONS = Set.of("RATIO", "QUANTITY", "ALL");

    private final AutoInvestSettingMapper settingMapper;
    private final AutoInvestStockMapper stockMapper;
    private final AutoInvestExecutionMapper executionMapper;
    private final AutoInvestTriggerMapper triggerMapper;
    private final StockMapper stockMasterMapper;
    private final SecuritiesAccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;

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

    /** 종목별 모으기 회차 내역(회차 desc) — 소유 검증 후 반환. */
    @Transactional(readOnly = true)
    public List<AutoInvestExecutionResponse> getExecutions(Long userId, Long id) {
        requireUser(userId);
        if (stockMapper.findByIdAndUserId(id, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        return executionMapper.findByStock(id).stream()
                .map(AutoInvestExecutionResponse::from)
                .toList();
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

    // ---- 트리거(물타기/익절) ----

    /** 트리거 등록/수정 — 종목 소유 검증 후 upsert(종목당 매수1·매도1, 재등록 시 is_armed 리셋). */
    @Transactional
    public AutoInvestTriggerResponse registerTrigger(Long userId, Long stockId, AutoInvestTriggerRequest req) {
        requireUser(userId);
        AutoInvestStock stock = stockMapper.findByIdAndUserId(stockId, userId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        AutoInvestTrigger trigger = validateTrigger(req, stock.getCurrency());
        trigger.setAutoInvestStockId(stockId);
        triggerMapper.upsert(trigger);
        AutoInvestTrigger saved = triggerMapper.findByStockId(stockId).stream()
                .filter(t -> t.getTriggerKind().equals(trigger.getTriggerKind()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "트리거 저장 확인 실패"));
        // 실시간 감지 엔진에 등록(#194) — 커밋 후 그 종목 호가 구독 ON·인덱스 적재.
        eventPublisher.publishEvent(new TriggerArmedEvent(saved.getId(), stock.getStockCode()));
        return AutoInvestTriggerResponse.from(saved);
    }

    /** 종목 트리거 목록(매수/매도). */
    @Transactional(readOnly = true)
    public List<AutoInvestTriggerResponse> getTriggers(Long userId, Long stockId) {
        requireUser(userId);
        if (stockMapper.findByIdAndUserId(stockId, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        return triggerMapper.findByStockId(stockId).stream()
                .map(AutoInvestTriggerResponse::from)
                .toList();
    }

    /** 트리거 해제. */
    @Transactional
    public void removeTrigger(Long userId, Long stockId, Long triggerId) {
        requireUser(userId);
        AutoInvestStock stock = stockMapper.findByIdAndUserId(stockId, userId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "자동모으기 설정을 찾을 수 없습니다.");
        }
        if (triggerMapper.deleteByIdAndStockId(triggerId, stockId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "트리거를 찾을 수 없습니다.");
        }
        // 실시간 감지 엔진에서 제거(#194) — 커밋 후 그 종목 트리거 0건이면 구독 OFF.
        eventPublisher.publishEvent(new TriggerDisarmedEvent(triggerId, stock.getStockCode()));
    }

    /** 트리거 검증 + 엔티티 구성. BUY(물타기)=수익률 음수·AMOUNT/QUANTITY / SELL(익절)=수익률 양수·RATIO/QUANTITY/ALL. */
    private AutoInvestTrigger validateTrigger(AutoInvestTriggerRequest req, String currency) {
        String kind = normalize(req.triggerKind());
        BigDecimal rate = req.conditionRate();
        if (rate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "발동 수익률(conditionRate)을 입력해주세요.");
        }
        String action = normalize(req.actionType());
        AutoInvestTrigger.AutoInvestTriggerBuilder b = AutoInvestTrigger.builder()
                .triggerKind(kind).conditionRate(rate).actionType(action);

        if ("BUY".equals(kind)) {
            if (rate.signum() >= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "물타기(BUY) 발동 수익률은 음수여야 합니다(예 -7).");
            }
            if (!BUY_ACTIONS.contains(action)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "물타기 action은 AMOUNT 또는 QUANTITY입니다.");
            }
            if ("AMOUNT".equals(action)) {
                requirePositiveInput(req.actionAmount(), "추가매수 금액");
                requireMinOrder(req.actionAmount(), currency);
                b.actionAmount(req.actionAmount());
            } else {
                requirePositiveInput(req.actionQuantity(), "추가매수 수량");
                b.actionQuantity(req.actionQuantity());
            }
        } else if ("SELL".equals(kind)) {
            if (rate.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "익절(SELL) 발동 수익률은 양수여야 합니다(예 +15).");
            }
            if (!SELL_ACTIONS.contains(action)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "익절 action은 RATIO·QUANTITY·ALL 중 하나입니다.");
            }
            if ("RATIO".equals(action)) {
                BigDecimal ratio = req.actionRatio();
                if (ratio == null || ratio.signum() <= 0 || ratio.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "매도 비율(actionRatio)은 0 초과 100 이하입니다.");
                }
                b.actionRatio(ratio);
            } else if ("QUANTITY".equals(action)) {
                requirePositiveInput(req.actionQuantity(), "매도 수량");
                b.actionQuantity(req.actionQuantity());
            }
            // ALL: 추가 입력 없음(보유 전량)
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "triggerKind는 BUY 또는 SELL이어야 합니다.");
        }
        return b.build();
    }

    private void requirePositiveInput(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "을(를) 입력해주세요.");
        }
    }

    private void requireMinOrder(BigDecimal amount, String currency) {
        boolean domestic = CURRENCY_KRW.equals(currency);
        BigDecimal min = domestic ? MIN_ORDER_KRW : MIN_ORDER_USD;
        if (amount.compareTo(min) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "최소 매수금액은 " + (domestic ? "1,000원" : "$1") + "입니다.");
        }
    }

    // ---- 내부 ----

    /** 종목 등록 시 마스터 스위치 ON 보장 — 없으면 기본 설정 생성, 있으면(시드/이전 OFF 포함) is_enabled=TRUE로 켠다. */
    private void ensureSettings(Long userId) {
        if (settingMapper.findByUserId(userId) == null) {
            settingMapper.insert(AutoInvestSetting.builder()
                    .userId(userId).isEnabled(true).isPaused(false).keepCollectingOnPause(true).build());
        } else {
            settingMapper.enable(userId);   // 기존 행이 OFF(시드 FALSE 등)여도 등록=시작 의사 → 켠다(is_paused 불변)
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
                        "최소 매수금액은 " + (domestic ? "1,000원" : "$1") + "입니다.");
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
