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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

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
        BigDecimal krw = requirePositive(req.krwAmount());
        txnAuthGuard.requireTxnAuth(userId);

        BigDecimal mid = baseRate();
        BigDecimal buyRate = ratePolicy.buyRate(USD, mid);
        BigDecimal usd = krw.divide(buyRate, USD_SCALE, RoundingMode.DOWN);

        FxTransaction tx = record(userId, KRW, krw, USD, usd, buyRate);
        FxLegResult legs = cmaFunds.applyFxLegs(userId, KRW, krw, USD, usd, tx.getId());
        // 회사쪽 복식부기 leg(H5): 회사 통화풀 2-leg(KRW 수취·USD 지급) + 실현 환차익. 같은 로컬 트랜잭션.
        firmLeg.record(tx.getId(), KRW, krw, USD, usd, mid, buyRate);

        return new KrwToUsdResponse(usd, buyRate, FEE, TRIGGER_MANUAL, legs.remainFrom());
    }

    /** 달러 → 원화: USD 풀 차감 → KRW = usd × 매도환율. */
    @Transactional
    public UsdToKrwResponse usdToKrw(Long userId, UsdToKrwRequest req) {
        requireUser(userId);
        BigDecimal usd = requirePositive(req.usdAmount());
        txnAuthGuard.requireTxnAuth(userId);

        BigDecimal mid = baseRate();
        BigDecimal sellRate = ratePolicy.sellRate(USD, mid);
        BigDecimal krw = usd.multiply(sellRate).setScale(KRW_SCALE, RoundingMode.DOWN);

        FxTransaction tx = record(userId, USD, usd, KRW, krw, sellRate);
        FxLegResult legs = cmaFunds.applyFxLegs(userId, USD, usd, KRW, krw, tx.getId());
        // 회사쪽 복식부기 leg(H5): 회사 통화풀 2-leg(USD 수취·KRW 지급) + 실현 환차익. 같은 로컬 트랜잭션.
        firmLeg.record(tx.getId(), USD, usd, KRW, krw, mid, sellRate);

        return new UsdToKrwResponse(krw, sellRate, FEE, TRIGGER_MANUAL, legs.remainFrom());
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
                                 String to, BigDecimal toAmount, BigDecimal rate) {
        FxTransaction tx = new FxTransaction();
        tx.setUserId(userId);
        tx.setFromCurrency(from);
        tx.setToCurrency(to);
        tx.setFromAmount(fromAmount);
        tx.setToAmount(toAmount);
        tx.setExchangeRate(rate);
        tx.setFee(FEE);
        tx.setTriggerType(TRIGGER_MANUAL);
        tx.setStatus(STATUS_COMPLETED);
        // TODO: 실서비스는 클라이언트 제공 멱등키(헤더) 사용. MVP는 자체 생성(중복 차단만 충족).
        tx.setIdempotencyKey(UUID.randomUUID().toString());
        fxMapper.insert(tx);
        return tx;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환전 금액은 0보다 커야 합니다.");
        }
        return amount;
    }
}
