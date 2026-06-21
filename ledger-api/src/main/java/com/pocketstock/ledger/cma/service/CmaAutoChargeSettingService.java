package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.cma.domain.CmaAutoChargeSetting;
import com.pocketstock.ledger.cma.dto.request.AutoChargeSettingRequest;
import com.pocketstock.ledger.cma.dto.response.AutoChargeSettingResponse;
import com.pocketstock.ledger.cma.mapper.CmaAutoChargeSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 부족금액 자동충전 설정(SETTLE-006, 후속1) — 설정 CRUD만 담당.
 *
 * <p>실행(부족분 감지 → 연동 은행계좌에서 교차 DB 자동이체)은 후속2로 분리(E-5). 여기서는
 * 켜고 끄기 + 충전 재원·1회 한도 저장만 한다.
 *
 * <p>충전 재원 {@code sourceAccountId}는 DB A {@code linked_bank_accounts.id}라 DB B에서 JOIN할
 * 수 없으므로, 소유 검증은 core-api Feign({@link AssetFeignClient#getLinkedAccounts})으로만 한다.
 * Feign은 userId의 계좌만 필터해 반환하므로, 결과가 비면 남의/없는 계좌로 본다.
 */
@Service
@RequiredArgsConstructor
public class CmaAutoChargeSettingService {

    private final CmaAutoChargeSettingMapper settingMapper;
    private final AssetFeignClient assetFeignClient;

    @Transactional(readOnly = true)
    public AutoChargeSettingResponse get(Long userId) {
        CmaAutoChargeSetting setting = settingMapper.findByUserId(userId);
        return setting != null ? AutoChargeSettingResponse.from(setting)
                : AutoChargeSettingResponse.disabled();
    }

    /**
     * 자동충전 설정 변경.
     *
     * <p>소유 검증(Feign 외부 호출)은 트랜잭션 밖에서 먼저 수행한다 — 단일 {@code upsert} 한 건이라
     * 별도 트랜잭션 경계가 필요 없고, 외부 I/O 동안 DB 커넥션을 점유하지 않게 하기 위함이다.
     */
    public void update(Long userId, AutoChargeSettingRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());

        CmaAutoChargeSetting setting = new CmaAutoChargeSetting();
        setting.setUserId(userId);
        setting.setIsEnabled(enabled);

        if (enabled) {
            // ON일 때만 필수 항목 검증 + 충전 재원 소유 검증(Feign).
            validateForEnabled(userId, request);
            setting.setSourceAccountRef(request.sourceAccountId());
            setting.setMaxChargePerTx(request.maxChargePerTx());
        } else {
            // OFF는 충전 재원·한도를 비워 저장(끄기). 외부 의존(Feign) 없음.
            setting.setSourceAccountRef(null);
            setting.setMaxChargePerTx(null);
        }

        settingMapper.upsert(setting);
    }

    private void validateForEnabled(Long userId, AutoChargeSettingRequest request) {
        if (request.sourceAccountId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자동충전을 켜려면 충전 재원 계좌가 필요합니다.");
        }
        if (request.maxChargePerTx() == null || request.maxChargePerTx().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1회 충전 한도는 0보다 커야 합니다.");
        }

        List<LinkedAccountSummary> owned =
                assetFeignClient.getLinkedAccounts(userId, List.of(request.sourceAccountId()));
        if (owned.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인 명의의 연동 계좌가 아닙니다.");
        }
    }
}
