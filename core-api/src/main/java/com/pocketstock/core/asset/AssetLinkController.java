package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.asset.dto.BankLinkResponse;
import com.pocketstock.core.asset.dto.CardLinkResponse;
import com.pocketstock.core.asset.dto.FxLinkResponse;
import com.pocketstock.core.asset.dto.InstitutionLinkRequest;
import com.pocketstock.core.asset.dto.LinkAuthRequest;
import com.pocketstock.core.asset.dto.LinkAuthResponse;
import com.pocketstock.core.asset.dto.LinkRequest;
import com.pocketstock.core.asset.dto.LinkResponse;
import com.pocketstock.core.asset.dto.PointLinkResponse;
import com.pocketstock.core.asset.dto.RefreshResponse;
import com.pocketstock.core.asset.dto.SecuritiesLinkResponse;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자산 연동 실행 API(쓰기) — 마이데이터 통합인증·일괄/개별 연동·새로고침.
 * 조회는 {@link AssetQueryController}, 휴면 해지는 {@link AssetCloseController}.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetLinkController {

    private final AssetLinkService assetLinkService;

    /** 마이데이터 통합인증(mock) — authToken 발급(무상태). */
    @PostMapping("/links/auth")
    public ResponseEntity<ApiResponse<LinkAuthResponse>> auth(
            @CurrentUserId Long userId, @RequestBody @Valid LinkAuthRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("마이데이터 통합인증 성공", assetLinkService.authenticate(request)));
    }

    /** 선택 기관 일괄 연동(온보딩). */
    @PostMapping("/links")
    public ResponseEntity<ApiResponse<LinkResponse>> link(
            @CurrentUserId Long userId, @RequestBody @Valid LinkRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("자산 연동 성공", assetLinkService.linkBulk(userId, request)));
    }

    /** 은행 계좌 개별 연동. */
    @PostMapping("/links/bank")
    public ResponseEntity<ApiResponse<BankLinkResponse>> linkBank(
            @CurrentUserId Long userId, @RequestBody @Valid InstitutionLinkRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("은행 계좌 연동 성공",
                assetLinkService.linkBank(userId, request.companyCode())));
    }

    /** 카드 개별 연동. */
    @PostMapping("/links/card")
    public ResponseEntity<ApiResponse<CardLinkResponse>> linkCard(
            @CurrentUserId Long userId, @RequestBody @Valid InstitutionLinkRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("카드 연동 성공",
                assetLinkService.linkCard(userId, request.companyCode())));
    }

    /** 포인트 개별 연동. */
    @PostMapping("/links/point")
    public ResponseEntity<ApiResponse<PointLinkResponse>> linkPoint(
            @CurrentUserId Long userId, @RequestBody @Valid InstitutionLinkRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("포인트 연동 성공",
                assetLinkService.linkPoint(userId, request.companyCode())));
    }

    /** SOL트래블 외화잔액 연동(본문 없음). */
    @PostMapping("/links/fx")
    public ResponseEntity<ApiResponse<FxLinkResponse>> linkFx(@CurrentUserId Long userId) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("SOL트래블 외화잔액 연동 성공", assetLinkService.linkFx(userId)));
    }

    /** 타 증권사 개별 연동. */
    @PostMapping("/links/securities")
    public ResponseEntity<ApiResponse<SecuritiesLinkResponse>> linkSecurities(
            @CurrentUserId Long userId, @RequestBody @Valid InstitutionLinkRequest request) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("타 증권사 연동 성공",
                assetLinkService.linkSecurities(userId, request.companyCode())));
    }

    /** 연동 자산 새로고침(no-op + last_synced_at). */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@CurrentUserId Long userId) {
        requireUser(userId);
        return ResponseEntity.ok(ApiResponse.ok("자산 새로고침 성공", assetLinkService.refresh(userId)));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
