package com.pocketstock.ledger.cma.controller;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.dto.request.InternalCmaCreditRequest;
import com.pocketstock.ledger.cma.dto.response.CmaBalanceResponse;
import com.pocketstock.ledger.cma.dto.response.CmaCreditResponse;
import com.pocketstock.ledger.cma.dto.response.CollectionSettingView;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import com.pocketstock.ledger.cma.service.CmaDormantCreditService;
import com.pocketstock.ledger.cma.service.CmaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * core→ledger 내부 호출 전용. 잔돈 스캔(읽기: 끝전 임계값·활성 소스) + 휴면계좌 해지(쓰기: CMA 입금).
 * 인증은 @CurrentUserId 대신 본문/파라미터 userId — core의 {@code /internal/assets/*}와 동일 패턴.
 */
@RestController
@RequestMapping("/internal/cma")
@RequiredArgsConstructor
public class InternalCmaController {

    private final CollectionSettingMapper collectionSettingMapper;
    private final CmaDormantCreditService dormantCreditService;
    private final CmaQueryService cmaQueryService;

    @GetMapping("/collection-settings")
    public List<CollectionSettingView> getCollectionSettings(@RequestParam Long userId) {
        return collectionSettingMapper.findByUserId(userId).stream()
                .map(CollectionSettingView::from)
                .toList();
    }

    /**
     * 휴면계좌 해지 입금 — core가 소프트 해지한 휴면 잔액을 사용자 CMA 풀에 {@code DORMANT}로 적립한다.
     * 멱등키는 ledger가 {@code DORMANT:{accountId}}로 강제(같은 계좌 재요청 시 이중 입금 방지).
     */
    @PostMapping("/credit")
    public CmaCreditResponse credit(@RequestBody InternalCmaCreditRequest request) {
        return new CmaCreditResponse(dormantCreditService.credit(request));
    }

    /** CMA 총 평가액(KRW 환산) — 자산 요약 집계용. 계좌 없으면 0 반환. */
    @GetMapping("/krw-total")
    public BigDecimal getCmaTotalKrw(@RequestParam Long userId) {
        try {
            return cmaQueryService.getBalance(userId).totalKrwEquivalent();
        } catch (BusinessException e) {
            if (ErrorCode.NOT_FOUND.equals(e.getErrorCode())) {
                return BigDecimal.ZERO;
            }
            throw e;
        }
    }

    /**
     * CMA의 KRW 지갑 잔액만 반환(외화 환산 제외) — 마이페이지 "포켓스톡 CMA 잔액(KRW)"용.
     * krw-total과 달리 USD 환산을 더하지 않는다. 계좌 미개설 시 0.
     */
    @GetMapping("/krw-balance")
    public BigDecimal getCmaKrwBalance(@RequestParam Long userId) {
        try {
            return cmaQueryService.getBalance(userId).accounts().stream()
                    .filter(a -> "KRW".equals(a.currency()))
                    .map(CmaBalanceResponse.BalanceItem::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (BusinessException e) {
            if (ErrorCode.NOT_FOUND.equals(e.getErrorCode())) {
                return BigDecimal.ZERO;
            }
            throw e;
        }
    }

    /**
     * 적립 소스타입 활성 일괄 변경 — 마이페이지 카드 잔돈 모으기 마스터 토글(sourceType=CARD)용.
     * 해당 타입 연동 행이 없으면 변경 0건(켤 대상이 없으면 무동작).
     */
    @PutMapping("/collection-settings/enabled")
    public void updateCollectionEnabled(
            @RequestParam Long userId,
            @RequestParam String sourceType,
            @RequestParam boolean enabled) {
        collectionSettingMapper.updateEnabledBySourceType(userId, sourceType, enabled);
    }
}
