package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import com.pocketstock.ledger.trading.domain.MaturityBuyReservation;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.MaturityReservationRequest;
import com.pocketstock.ledger.trading.dto.MaturityReservationResponse;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.MaturityReservationMapper;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 만기 후 배당주 매수 예약 CRUD — 생성·목록·취소. 등록 자체가 자동 매수 사전동의(자동모으기와 동일).
 * 실제 매수는 {@code MaturityReservationScheduler}가 만기일에 {@code place(source=MATURITY)}로 집행.
 *
 * <p>만기일·시장·통화는 서버가 정한다: {@code linkedBankAccountId}로 연동은행계좌(DB A, {@link AssetFeignClient})를
 * 조회해 예적금·소유·만기일을 검증·스냅샷하고, {@code stockCode}→거래소에서 국내(KRW)를 파생한다. 해외는 MVP 제외.
 */
@Service
@RequiredArgsConstructor
public class MaturityReservationService {

    private static final Set<String> DOMESTIC_EXCHANGES = Set.of("KOSPI", "KOSDAQ");
    private static final Set<String> DEPOSIT_TYPES = Set.of("DEPOSIT", "SAVINGS");
    private static final String MARKET_DOMESTIC = "DOMESTIC";
    private static final String CURRENCY_KRW = "KRW";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String SOURCE_MATURITY = "MATURITY";
    private static final String SIDE_BUY = "BUY";
    private static final String ORDER_TYPE_AMOUNT = "AMOUNT";
    private static final BigDecimal MIN_ORDER_KRW = BigDecimal.valueOf(1000);

    private final MaturityReservationMapper reservationMapper;
    private final StockMapper stockMapper;
    private final AssetFeignClient assetFeignClient;
    private final CmaChargePort chargePort;
    private final FractionalOrderService fractionalOrderService;

    /** 예약 생성 — 종목(국내)·계좌(예적금·만기) 검증 후 만기일 스냅샷으로 1행 INSERT. 같은 계좌·종목 중복 시 409. */
    @Transactional
    public MaturityReservationResponse create(Long userId, MaturityReservationRequest req) {
        requireUser(userId);
        Long accountId = requireAccountId(req.linkedBankAccountId());
        BigDecimal buyAmount = requireBuyAmount(req.buyAmount());
        TradableStock stock = resolveDomesticStock(req.stockCode());
        LinkedAccountSummary account = resolveMaturityAccount(userId, accountId);

        // 원금(현재 잔액)이 매수금액을 못 덮으면 거부 — 만기 시 잔액부족으로 집행 실패할 예약을 미리 차단.
        if (account.balance() == null || account.balance().compareTo(buyAmount) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "매수금액이 예적금 원금을 초과합니다. (원금 "
                            + (account.balance() == null ? BigDecimal.ZERO : account.balance())
                            + ", 매수 " + buyAmount + ")");
        }

        // 같은 계좌·종목 기존 예약 처리 — 진행 중(RESERVED)·집행완료(EXECUTED)는 거부,
        // 취소·실패(CANCELLED/FAILED)는 새 만기일·금액으로 되살린다(UNIQUE라 cancel 후 재예약이 막히지 않게, #11).
        MaturityBuyReservation existing =
                reservationMapper.findByUserAccountStock(userId, accountId, stock.getStockCode());
        if (existing != null) {
            if (STATUS_RESERVED.equals(existing.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 같은 계좌·종목으로 예약된 매수가 있습니다.");
            }
            if (STATUS_EXECUTED.equals(existing.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 집행된 예약입니다.");
            }
            reservationMapper.revive(existing.getId(), account.maturityDate(), buyAmount);
            existing.setStatus(STATUS_RESERVED);
            existing.setMaturityDate(account.maturityDate());
            existing.setBuyAmount(buyAmount);
            existing.setOrderId(null);
            existing.setFailReason(null);
            existing.setExecutedAt(null);
            existing.setStockName(stock.getStockName());
            return MaturityReservationResponse.from(existing);
        }

        MaturityBuyReservation entity = MaturityBuyReservation.builder()
                .userId(userId)
                .linkedBankAccountId(accountId)
                .maturityDate(account.maturityDate())
                .stockCode(stock.getStockCode())
                .market(MARKET_DOMESTIC)
                .currency(CURRENCY_KRW)
                .buyAmount(buyAmount)
                .status(STATUS_RESERVED)
                .build();
        try {
            reservationMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 동시 생성 경합(둘 다 위 조회를 통과) — UNIQUE가 두 번째를 막음.
            throw new BusinessException(ErrorCode.CONFLICT, "이미 같은 계좌·종목으로 예약된 매수가 있습니다.");
        }
        entity.setStockName(stock.getStockName());
        return MaturityReservationResponse.from(entity);
    }

    /** 내 예약 목록(최신순). */
    @Transactional(readOnly = true)
    public List<MaturityReservationResponse> list(Long userId) {
        requireUser(userId);
        return reservationMapper.findByUserId(userId).stream()
                .map(MaturityReservationResponse::from)
                .toList();
    }

    /** 예약 취소 — RESERVED 상태에서만(이미 집행/취소면 거부). user_id 가드. */
    @Transactional
    public void cancel(Long userId, Long id) {
        requireUser(userId);
        int updated = reservationMapper.cancel(id, userId);
        if (updated == 0) {
            MaturityBuyReservation existing = reservationMapper.findByIdAndUserId(id, userId);
            if (existing == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "예약을 찾을 수 없습니다.");
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 집행되었거나 취소된 예약입니다.");
        }
    }

    /**
     * 만기 도래 예약 1건 집행 — 만기 원금 → CMA 충전 후 소수점 매수. <b>한 트랜잭션</b>이라 매수 실패 시 충전도 롤백
     * (만기계좌만 차감되고 매수 안 되는 부분반영 방지). 스케줄러가 호출하며, 실패는 BusinessException으로 던져
     * 스케줄러가 FAILED로 기록한다(자동모으기 {@code executeOne}과 동형).
     *
     * <p>멱등: CMA 충전키 {@code MATURITY_{id}:charge}·매수 client_order_id {@code MATURITY_{id}} 모두 결정적 →
     * 재시도/멀티인스턴스 중복 충전·매수 차단. 단일 활성 인스턴스(LedgerActivation)만 스케줄러를 돌린다.
     *
     * @return 생성된 주문 id(온주분 우선, 없으면 소수분). 추적용으로 예약에 저장.
     */
    @Transactional
    public Long executeReservation(MaturityBuyReservation r) {
        // ① 만기 원금 → CMA 원화풀 충전(정확히 매수금액). 출처=만기 연동계좌. 소유·잔액·멱등은 포트가 검증.
        chargePort.charge(r.getUserId(), r.getLinkedBankAccountId(), r.getBuyAmount(),
                SOURCE_MATURITY + "_" + r.getId() + ":charge");
        // ② 소수점 매수 — 정수=온주 즉시·끝수=소수 차수(엔진 내부 split). orders.source=MATURITY로 추적.
        FractionalOrderRequest req = new FractionalOrderRequest(
                SOURCE_MATURITY + "_" + r.getId(), r.getStockCode(), SIDE_BUY,
                ORDER_TYPE_AMOUNT, r.getBuyAmount(), null);
        SplitOrderResponse resp = fractionalOrderService.place(r.getUserId(), req, SOURCE_MATURITY);
        return resp.wholeOrderId() != null ? resp.wholeOrderId() : resp.fractionalOrderId();
    }

    /** 종목 해석 — 존재·거래가능·국내(KRW)만. 해외 배당주는 환전 경로라 MVP 제외. */
    private TradableStock resolveDomesticStock(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종목코드를 입력해주세요.");
        }
        TradableStock stock = stockMapper.findByCode(stockCode.trim());
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + stockCode);
        }
        if (Boolean.FALSE.equals(stock.getIsActive())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "거래 정지 종목입니다: " + stockCode);
        }
        if (!DOMESTIC_EXCHANGES.contains(stock.getExchange())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "만기 매수 예약은 현재 국내 배당주만 지원합니다.");
        }
        return stock;
    }

    /** 만기 계좌 해석 — 본인 소유 + 예적금(DEPOSIT/SAVINGS) + 만기일 존재 + 미래 만기. */
    private LinkedAccountSummary resolveMaturityAccount(Long userId, Long accountId) {
        List<LinkedAccountSummary> accounts = assetFeignClient.getLinkedAccounts(userId, List.of(accountId));
        if (accounts.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 대상으로 쓸 수 없는 계좌입니다.");
        }
        LinkedAccountSummary account = accounts.get(0);
        if (account.accountType() == null || !DEPOSIT_TYPES.contains(account.accountType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예금·적금 계좌만 만기 매수 예약을 걸 수 있습니다.");
        }
        if (account.maturityDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "만기일이 없는 계좌입니다.");
        }
        if (account.maturityDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 만기가 지난 계좌입니다.");
        }
        return account;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private Long requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "만기 계좌(linkedBankAccountId)가 필요합니다.");
        }
        return accountId;
    }

    private BigDecimal requireBuyAmount(BigDecimal buyAmount) {
        if (buyAmount == null || buyAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "매수금액은 0보다 커야 합니다.");
        }
        if (buyAmount.compareTo(MIN_ORDER_KRW) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "최소 매수금액은 1,000원입니다.");
        }
        return buyAmount;
    }
}
