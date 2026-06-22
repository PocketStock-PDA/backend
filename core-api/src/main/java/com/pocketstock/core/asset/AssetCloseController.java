package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.DormantCloseRequest;
import com.pocketstock.core.asset.dto.DormantCloseResponse;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연동 자산 실행 API(쓰기) — 휴면계좌 일괄 해지.
 * 조회(읽기)는 {@link AssetQueryController}, 연동 실행(links/refresh)은 후속 컨트롤러.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetCloseController {

    private final AssetCloseService assetCloseService;

    /** 휴면계좌 다중 선택 일괄 소프트 해지 → 잔액 CMA 풀로 이체(DORMANT 입금). */
    @PostMapping("/dormant/close")
    public ResponseEntity<ApiResponse<DormantCloseResponse>> closeDormant(
            @CurrentUserId Long userId,
            @RequestBody @Valid DormantCloseRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        DormantCloseResponse data = assetCloseService.closeDormantAccounts(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("휴면계좌 해지 및 CMA 이체 성공", data));
    }
}
