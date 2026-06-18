package com.pocketstock.ledger.cma.service;

import com.pocketstock.ledger.cma.domain.CollectionSetting;
import com.pocketstock.ledger.cma.dto.request.CollectionSettingRequest;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CmaCollectService {

    private final CollectionSettingMapper settingMapper;

    private static final java.util.Set<Integer> VALID_THRESHOLDS = java.util.Set.of(1000, 5000, 10000);

    @Transactional
    public void updateSettings(Long userId, CollectionSettingRequest request) {
        for (CollectionSettingRequest.SettingItem item : request.settings()) {
            Integer threshold = item.threshold();
            if (threshold != null && !VALID_THRESHOLDS.contains(threshold)) {
                throw new com.pocketstock.common.exception.BusinessException(
                        com.pocketstock.common.exception.ErrorCode.INVALID_INPUT,
                        "threshold는 1000, 5000, 10000 중 하나여야 합니다.");
            }

            CollectionSetting setting = new CollectionSetting();
            setting.setUserId(userId);
            setting.setSourceType(item.sourceType());
            setting.setSourceRefId(item.sourceRefId());
            setting.setIsEnabled(item.enabled());
            setting.setThreshold(threshold != null ? threshold : 10000);
            settingMapper.upsert(setting);
        }
    }
}
