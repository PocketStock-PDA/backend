package com.pocketstock.core.recommendations.deposit;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.recommendations.deposit.dto.DepositProductDto;
import com.pocketstock.core.recommendations.deposit.dto.DepositRolloverDto;
import com.pocketstock.core.recommendations.deposit.dto.DepositRolloverRequest;
import com.pocketstock.core.recommendations.deposit.dto.DueCmaTransfer;
import com.pocketstock.core.recommendations.deposit.dto.RolloverSourceAccount;
import com.pocketstock.core.recommendations.deposit.mapper.DepositMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositMapper mapper;

    /** 예적금 상품 추천 목록(최고금리 순). */
    public List<DepositProductDto> listProducts() {
        return mapper.findActiveProducts();
    }

    /** 유저의 '예금 재예치' 기록 — 전환내역용. */
    public List<DepositRolloverDto> listRollovers(Long userId) {
        return mapper.findRolloversByUser(userId);
    }

    /** '예금 재예치' 생성 — 만기된 예적금과 '같은 상품'으로 재예치(상품명·금리·기간을 계좌에서 스냅샷). */
    public void createRollover(Long userId, DepositRolloverRequest req) {
        if (req.linkedBankAccountId() == null || req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "재예치 요청 값이 올바르지 않습니다.");
        }
        RolloverSourceAccount acc = mapper.findRolloverSource(userId, req.linkedBankAccountId());
        if (acc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "예적금 계좌를 찾을 수 없습니다.");
        }
        // 예금 재예치와 CMA 이체는 동시 불가 — 한 계좌의 남은 자금은 둘 중 하나로만 굴린다.
        requireNoActiveRollover(userId, req.linkedBankAccountId());
        String productType = "SAVINGS".equals(acc.accountType()) ? "정기적금" : "정기예금";
        // 약정 연이율(소수) → 표시용 퍼센트. 같은 상품이라 기본=최고 동일.
        BigDecimal ratePct = acc.interestRate() == null
                ? BigDecimal.ZERO
                : acc.interestRate().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        // 가입기간(개월) = 가입일~만기일. 정보 없으면 12개월 기본.
        int periodMonths = 12;
        if (acc.startDate() != null && acc.maturityDate() != null) {
            long months = ChronoUnit.MONTHS.between(acc.startDate(), acc.maturityDate());
            if (months >= 1) {
                periodMonths = (int) months;
            }
        }
        mapper.insertRollover(
                userId,
                req.linkedBankAccountId(),
                acc.maturityDate(),
                acc.accountName(),
                productType,
                req.amount(),
                ratePct,
                ratePct,
                periodMonths);
    }

    /**
     * '재예치 없이 CMA로 이체' 예약 — 실제 계좌→CMA 이동은 만기일에 ledger 스케줄러가 집행한다(배당주 매수와 동일 타이밍).
     * 여기서는 만기일 집행 대상으로 기록(status=RESERVED, product_type='CMA')만 한다.
     */
    public void transferToCma(Long userId, DepositRolloverRequest req) {
        if (req.linkedBankAccountId() == null || req.amount() == null || req.amount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이체 요청 값이 올바르지 않습니다.");
        }
        RolloverSourceAccount acc = mapper.findRolloverSource(userId, req.linkedBankAccountId());
        if (acc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "예적금 계좌를 찾을 수 없습니다.");
        }
        // 예금 재예치와 CMA 이체는 동시 불가 — 한 계좌의 남은 자금은 둘 중 하나로만 굴린다.
        requireNoActiveRollover(userId, req.linkedBankAccountId());
        // 목적지=CMA(상품 아님, 금리·기간 없음). 만기일에 스케줄러가 계좌→CMA 집행.
        mapper.insertRollover(
                userId,
                req.linkedBankAccountId(),
                acc.maturityDate(),
                "포켓스톡 CMA",
                "CMA",
                req.amount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0);
    }

    /** 예금 재예치·CMA 이체 동시 예약 차단 — 한 계좌엔 활성(RESERVED) 굴리기 하나만. */
    private void requireNoActiveRollover(Long userId, Long accountId) {
        if (mapper.countActiveRolloversByAccount(userId, accountId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 이 예적금의 남은 자금을 굴리도록 예약돼 있어요. 예금 재예치와 CMA 이체는 함께 할 수 없어요.");
        }
    }

    /** 만기 도래한 CMA 이체 예약 — ledger 스케줄러가 만기일에 집행할 대상. */
    public List<DueCmaTransfer> listDueCmaTransfers(LocalDate date) {
        return mapper.findDueCmaTransfers(date);
    }

    /**
     * 만기 이자 입금(1회, 멱등) — 만기 굴리기 집행(배당주 매수·CMA 이체) 직전에 호출해 잔액=총 수령액(원금+이자)으로 만든다.
     * 이미 입금됐으면 아무 일도 하지 않는다(중복 입금 차단). ledger 만기 스케줄러가 계좌별로 호출.
     */
    public void settleMaturityInterest(Long userId, Long accountId) {
        mapper.creditMaturityInterestOnce(userId, accountId);
    }

    /** CMA 이체 예약 상태 갱신(EXECUTED/FAILED) — 스케줄러 집행 결과 반영. */
    public void markRolloverStatus(Long id, String status) {
        mapper.updateRolloverStatus(id, status);
    }
}
