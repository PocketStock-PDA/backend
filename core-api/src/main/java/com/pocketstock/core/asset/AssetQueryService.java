package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.DormantAccountResponse;
import com.pocketstock.core.asset.dto.ExternalHoldingResponse;
import com.pocketstock.core.asset.dto.ExternalHoldingRow;
import com.pocketstock.core.asset.dto.InstitutionResponse;
import com.pocketstock.core.asset.mapper.ExternalHoldingMapper;
import com.pocketstock.core.asset.mapper.InstitutionMapper;
import com.pocketstock.core.asset.mapper.LinkedAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 연동 자산 조회 전용 서비스(쓰기 없음).
 * 연동 가능 기관 목록 / 휴면 계좌 / 타사 보유 소수점 조회를 담당한다.
 * scan(잠자는 잔돈)은 ledger threshold 설정 반영(F-E)이 필요해 별도 후속 구현.
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final InstitutionMapper institutionMapper;
    private final LinkedAssetMapper linkedAssetMapper;
    private final ExternalHoldingMapper externalHoldingMapper;

    public List<InstitutionResponse> getInstitutions(Long userId) {
        return institutionMapper.findInstitutions(userId);
    }

    public List<DormantAccountResponse> getDormantAccounts(Long userId) {
        return linkedAssetMapper.findDormantAccounts(userId);
    }

    /** 평면 행을 증권사(companyCode) 단위로 묶는다. 조회 순서(sort_order) 보존 위해 LinkedHashMap. */
    public List<ExternalHoldingResponse> getExternalHoldings(Long userId) {
        List<ExternalHoldingRow> rows = externalHoldingMapper.findExternalHoldings(userId);

        Map<String, ExternalHoldingResponse> grouped = new LinkedHashMap<>();
        for (ExternalHoldingRow row : rows) {
            ExternalHoldingResponse company = grouped.computeIfAbsent(
                    row.companyCode(),
                    code -> new ExternalHoldingResponse(row.companyCode(), row.companyName(), new ArrayList<>()));
            company.stocks().add(new ExternalHoldingResponse.Holding(
                    row.stockCode(), row.stockName(), row.quantity(), row.evaluated()));
        }
        return new ArrayList<>(grouped.values());
    }
}
