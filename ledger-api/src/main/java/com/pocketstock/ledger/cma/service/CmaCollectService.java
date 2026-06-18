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

    @Transactional
    public void updateSettings(Long userId, CollectionSettingRequest request) {
        for (CollectionSettingRequest.SettingItem item : request.settings()) {
            CollectionSetting setting = new CollectionSetting();
            setting.setUserId(userId);
            setting.setSourceType(item.sourceType());
            setting.setSourceRefId(item.sourceRefId());
            setting.setIsEnabled(item.enabled());
            settingMapper.upsert(setting);
        }
    }
}
