package com.pocketstock.ledger.cma.controller;

import com.pocketstock.ledger.cma.dto.request.InternalCmaCreditRequest;
import com.pocketstock.ledger.cma.dto.response.CmaCreditResponse;
import com.pocketstock.ledger.cma.dto.response.CollectionSettingView;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import com.pocketstock.ledger.cma.service.CmaDormantCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
