package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.DormantAccountResponse;
import com.pocketstock.core.asset.dto.ExternalHoldingResponse;
import com.pocketstock.core.asset.dto.InstitutionResponse;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 연동 자산 조회 API(읽기 전용).
 * 연동 가능 기관 / 휴면 계좌 / 타사 보유 소수점 조회.
 * 연동 실행(POST /links/*)·새로고침·휴면 해지는 별도 컨트롤러(후속).
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetQueryController {

    private final AssetQueryService assetQueryService;

    /** 연동 가능 기관 목록(카탈로그 + 유저 연동상태). */
    @GetMapping("/institutions")
    public ResponseEntity<ApiResponse<List<InstitutionResponse>>> getInstitutions(
            @CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<InstitutionResponse> data = assetQueryService.getInstitutions(userId);
        return ResponseEntity.ok(ApiResponse.ok("연동 가능 기관 목록 조회 성공", data));
    }

    /** 휴면계좌 조회. */
    @GetMapping("/dormant")
    public ResponseEntity<ApiResponse<List<DormantAccountResponse>>> getDormantAccounts(
            @CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<DormantAccountResponse> data = assetQueryService.getDormantAccounts(userId);
        return ResponseEntity.ok(ApiResponse.ok("휴면계좌 조회 성공", data));
    }

    /** 타사 보유 소수점 통합 조회(증권사 단위 그룹). */
    @GetMapping("/external-holdings")
    public ResponseEntity<ApiResponse<List<ExternalHoldingResponse>>> getExternalHoldings(
            @CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<ExternalHoldingResponse> data = assetQueryService.getExternalHoldings(userId);
        return ResponseEntity.ok(ApiResponse.ok("타사 보유 소수점 조회 성공", data));
    }
}
