package com.pocketstock.ledger.exchange.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.ExchangeRatePolicy;
import com.pocketstock.ledger.exchange.domain.FxTransaction;
import com.pocketstock.ledger.exchange.dto.request.KrwToUsdRequest;
import com.pocketstock.ledger.exchange.dto.request.UsdToKrwRequest;
import com.pocketstock.ledger.exchange.dto.response.KrwToUsdResponse;
import com.pocketstock.ledger.exchange.dto.response.UsdToKrwResponse;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.exchange.mapper.FxTransactionMapper;
import com.pocketstock.ledger.exchange.port.CmaFundsPort;
import com.pocketstock.ledger.exchange.port.FxLegResult;
import com.pocketstock.user.security.TxnAuthGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 수동 환전 체결 — CMA 내부 통화 스왑(ERD-07 §고민-11).
 *
 * <p>한 트랜잭션 안에서: ① 환율 스냅샷({@link CurrencyRateCache}) → 적용환율({@link ExchangeRatePolicy})
 * 산정 → ② {@code fx_transactions} 적재(status=COMPLETED, 환율 박제) → ③ CMA 풀 차감/입금
 * ({@link CmaFundsPort}). cma·exchange가 같은 DB B라 Saga 없이 로컬 트랜잭션 + 실패 시 롤백.
 *
 * <p>잔액(원화/달러)은 exchange가 보유하지 않는다 — 차감/입금과 잔액 조회는 CMA(포트)가 담당.
 * MVP: mock 즉시 체결(즉시 COMPLETED), trigger=MANUAL, fee=0(비용은 환율 내재).
 */
@Service
@RequiredArgsConstructor
public class ExchangeSettleService {

    private static final String USD = "USD";
    private static final String KRW = "KRW";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final String TRIGGER_AUTO = "AUTO";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final BigDecimal FEE = BigDecimal.ZERO;

    /** USD는 센트(2자리), KRW는 원(0자리). 고객 불리 방향으로 절사(DOWN). */
    private static final int USD_SCALE = 2;
    private static final int KRW_SCALE = 0;

    private final CurrencyRateCache rateCache;
    private final ExchangeRatePolicy ratePolicy;
    private final FxTransactionMapper fxMapper;
    private final CmaFundsPort cmaFunds;
    private final FxFirmLegService firmLeg;
    private final TxnAuthGuard txnAuthGuard;

    /** 원화 → 달러: KRW 풀 차감 → USD = krw ÷ 매수환율. */
    @Transactional
    public KrwToUsdResponse krwToUsd(Long userId, KrwToUsdRequest req) {
        requireUser(userId);
        String key = requireKey(req.idempotencyKey());
        BigDecimal krw = requirePositive(req.krwAmount());

        // 멱등 재요청: 같은 키 환전이 이미 있으면 leg 재적용 없이 기존 결과 반환(잔액은 현재 풀 조회).
        FxTransaction existing = fxMapper.findByIdempotencyKey(key);
        if (existing != null) {
            requireOwner(existing, userId);
            return new KrwToUsdResponse(existing.getToAmount(), existing.getExchangeRate(),
                    existing.getFee(), existing.getTriggerType(), cmaFunds.poolBalance(userId, KRW));
        }

        txnAuthGuard.requireTxnAuth(userId);
        BigDecimal mid = baseRate();
        BigDecimal buyRate = ratePolicy.buyRate(USD, mid);
        BigDecimal usd = krw.divide(buyRate, USD_SCALE, RoundingMode.DOWN);

        try {
            FxTransaction tx = record(userId, KRW, krw, USD, usd, buyRate, TRIGGER_MANUAL, null, key);
            FxLegResult legs = cmaFunds.applyFxLegs(userId, KRW, krw, USD, usd, tx.getId());
            // 회사쪽 복식부기 leg(H5): 회사 통화풀 2-leg(KRW 수취·USD 지급) + 실현 환차익. 같은 로컬 트랜잭션.
            firmLeg.record(tx.getId(), KRW, krw, USD, usd, mid, buyRate);
            return new KrwToUsdResponse(usd, buyRate, FEE, TRIGGER_MANUAL, legs.remainFrom());
        } catch (DuplicateKeyException e) {
            // 거의 동시에 같은 키 2건이 위 단락을 통과한 경합 — 회사 leg UNIQUE가 두 번째를 막아 롤백. 재요청 권장.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 환전입니다.");
        }
    }

    /** 달러 → 원화: USD 풀 차감 → KRW = usd × 매도환율. */
    @Transactional
    public UsdToKrwResponse usdToKrw(Long userId, UsdToKrwRequest req) {
        requireUser(userId);
        String key = requireKey(req.idempotencyKey());
        BigDecimal usd = requirePositive(req.usdAmount());

        // 멱등 재요청: 같은 키 환전이 이미 있으면 leg 재적용 없이 기존 결과 반환(잔액은 현재 풀 조회).
        FxTransaction existing = fxMapper.findByIdempotencyKey(key);
        if (existing != null) {
            requireOwner(existing, userId);
            return new UsdToKrwResponse(existing.getToAmount(), existing.getExchangeRate(),
                    existing.getFee(), existing.getTriggerType(), cmaFunds.poolBalance(userId, USD));
        }

        txnAuthGuard.requireTxnAuth(userId);
        BigDecimal mid = baseRate();
        BigDecimal sellRate = ratePolicy.sellRate(USD, mid);
        BigDecimal krw = usd.multiply(sellRate).setScale(KRW_SCALE, RoundingMode.DOWN);

        try {
            FxTransaction tx = record(userId, USD, usd, KRW, krw, sellRate, TRIGGER_MANUAL, null, key);
            FxLegResult legs = cmaFunds.applyFxLegs(userId, USD, usd, KRW, krw, tx.getId());
            // 회사쪽 복식부기 leg(H5): 회사 통화풀 2-leg(USD 수취·KRW 지급) + 실현 환차익. 같은 로컬 트랜잭션.
            firmLeg.record(tx.getId(), USD, usd, KRW, krw, mid, sellRate);
            return new UsdToKrwResponse(krw, sellRate, FEE, TRIGGER_MANUAL, legs.remainFrom());
        } catch (DuplicateKeyException e) {
            // 거의 동시에 같은 키 2건이 위 단락을 통과한 경합 — 회사 leg UNIQUE가 두 번째를 막아 롤백. 재요청 권장.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 환전입니다.");
        }
    }

    /**
     * 매수 자동환전(EXC-005, #172) — 주문 충당용 KRW→USD. 거래 인증은 매수 진입(컨트롤러)에서 1회 처리됐으므로
     * 여기선 우회한다. {@code targetUsd}를 채우게 KRW를 올림 역산하고, 환전 결과(≥targetUsd)는 달러풀에 적재된다
     * (초과분은 잔류). 멱등키는 주문 파생({@code order:{id}:autofx})이라 재시도/재충당에 이중 환전이 없다.
     * 호출자(매수 충당)의 트랜잭션에 합류해 실패 시 매수 전체가 롤백된다.
     *
     * @param targetUsd   달러풀에 채워야 할 USD(매수 부족분)
     * @param orderId     연계 매수주문(fx_transactions.ref_order_id)
     * @param maxKrwLimit fx_auto_settings.max_amount_per_tx — 환산 KRW가 초과하면 거부(거액 자동환전 차단). null=무제한.
     */
    @Transactional
    public void autoKrwToUsd(Long userId, BigDecimal targetUsd, Long orderId, BigDecimal maxKrwLimit) {
        String key = "order:" + orderId + ":autofx";
        if (fxMapper.findByIdempotencyKey(key) != null) {
            return;   // 멱등 — 이미 이 주문 자동환전 완료(재시도 안전)
        }
        BigDecimal mid = baseRate();
        BigDecimal buyRate = ratePolicy.buyRate(USD, mid);
        // 목표 USD를 채우게 KRW 올림 역산 — 환전 usd = krw÷buyRate ≥ targetUsd(초과분은 달러풀 잔류).
        BigDecimal krw = targetUsd.multiply(buyRate).setScale(KRW_SCALE, RoundingMode.UP);
        if (maxKrwLimit != null && krw.compareTo(maxKrwLimit) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "1회 자동환전 한도(" + maxKrwLimit + "원)를 초과합니다. 직접 환전 후 다시 시도해주세요.");
        }
        BigDecimal usd = krw.divide(buyRate, USD_SCALE, RoundingMode.DOWN);
        try {
            FxTransaction tx = record(userId, KRW, krw, USD, usd, buyRate, TRIGGER_AUTO, orderId, key);
            cmaFunds.applyFxLegs(userId, KRW, krw, USD, usd, tx.getId());
            firmLeg.record(tx.getId(), KRW, krw, USD, usd, mid, buyRate);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 처리 중인 자동환전입니다.");
        }
    }

    /** 캐시의 매매기준율(LS CUR). 콜드스타트(틱 미수신) 시 502. */
    private BigDecimal baseRate() {
        CurrencyRateResponse latest = rateCache.get();
        if (latest == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 정보를 아직 받지 못했습니다.");
        }
        return latest.exchangeRate();
    }

    /** fx_transactions 적재(COMPLETED). insert 후 채워진 id를 CMA 원장 ref_id로 넘긴다. */
    private FxTransaction record(Long userId, String from, BigDecimal fromAmount,
                                 String to, BigDecimal toAmount, BigDecimal rate,
                                 String triggerType, Long refOrderId, String idempotencyKey) {
        FxTransaction tx = new FxTransaction();
        tx.setUserId(userId);
        tx.setFromCurrency(from);
        tx.setToCurrency(to);
        tx.setFromAmount(fromAmount);
        tx.setToAmount(toAmount);
        tx.setExchangeRate(rate);
        tx.setFee(FEE);
        tx.setTriggerType(triggerType);     // MANUAL(수동) / AUTO(매수 자동환전 #172)
        tx.setRefOrderId(refOrderId);        // AUTO일 때 연계 매수주문, 수동은 null
        tx.setStatus(STATUS_COMPLETED);
        tx.setIdempotencyKey(idempotencyKey);   // 클라 발급 멱등키(#96 item3) — UNIQUE로 중복 환전 차단
        fxMapper.insert(tx);
        return tx;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 클라 발급 멱등키 필수 — 빈 값이면 거부(따닥 탭·재전송 방어가 무력해짐). */
    private String requireKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "멱등키(idempotencyKey)가 필요합니다.");
        }
        return key;
    }

    /** 멱등키는 전역 UNIQUE — 다른 유저 키와 충돌하면 남의 환전 노출 금지(409). */
    private void requireOwner(FxTransaction tx, Long userId) {
        if (!userId.equals(tx.getUserId())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 사용된 멱등키입니다.");
        }
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환전 금액은 0보다 커야 합니다.");
        }
        return amount;
    }
}
