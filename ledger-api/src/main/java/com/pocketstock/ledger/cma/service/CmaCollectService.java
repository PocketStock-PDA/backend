package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CollectionSetting;
import com.pocketstock.ledger.cma.dto.request.CollectionSettingRequest;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmaCollectService {

    private final CollectionSettingMapper settingMapper;

    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(10000);
    private static final List<BigDecimal> VALID_THRESHOLDS =
            List.of(BigDecimal.valueOf(1000), BigDecimal.valueOf(5000), BigDecimal.valueOf(10000));

    @Transactional
    public void updateSettings(Long userId, CollectionSettingRequest request) {
        Map<String, CollectionSetting> existingByKey = settingMapper.findByUserId(userId).stream()
                .collect(Collectors.toMap(this::settingKey, Function.identity()));

        for (CollectionSettingRequest.SettingItem item : request.settings()) {
            BigDecimal threshold = item.threshold();
            if (threshold != null && VALID_THRESHOLDS.stream().noneMatch(v -> v.compareTo(threshold) == 0)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "threshold는 1000, 5000, 10000 중 하나여야 합니다.");
            }

            CollectionSetting existing = existingByKey.get(item.sourceType() + ":" + item.sourceRefId());
            BigDecimal resolvedThreshold = threshold != null
                    ? threshold
                    : existing != null && existing.getThreshold() != null
                            ? existing.getThreshold()
                            : DEFAULT_THRESHOLD;

            CollectionSetting setting = new CollectionSetting();
            setting.setUserId(userId);
            setting.setSourceType(item.sourceType());
            setting.setSourceRefId(item.sourceRefId());
            setting.setIsEnabled(item.enabled());
            setting.setThreshold(resolvedThreshold);
            settingMapper.upsert(setting);
        }
    }

    private String settingKey(CollectionSetting setting) {
        return setting.getSourceType() + ":" + setting.getSourceRefId();
    }
}
