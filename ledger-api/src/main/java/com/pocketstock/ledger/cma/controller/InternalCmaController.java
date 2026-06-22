package com.pocketstock.ledger.cma.controller;

import com.pocketstock.ledger.cma.dto.response.CollectionSettingView;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * core→ledger 내부 호출 전용(읽기). 잔돈 스캔에서 끝전 임계값·활성 소스를 가져간다.
 * 인증은 @CurrentUserId 대신 userId 파라미터 — core의 {@code /internal/assets/*}와 동일 패턴.
 */
@RestController
@RequestMapping("/internal/cma")
@RequiredArgsConstructor
public class InternalCmaController {

    private final CollectionSettingMapper collectionSettingMapper;

    @GetMapping("/collection-settings")
    public List<CollectionSettingView> getCollectionSettings(@RequestParam Long userId) {
        return collectionSettingMapper.findByUserId(userId).stream()
                .map(CollectionSettingView::from)
                .toList();
    }
}
