package com.pocketstock.core.mypage;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.mypage.dto.MyPageSettings;
import com.pocketstock.core.mypage.dto.MyProfileResponse;
import com.pocketstock.core.mypage.dto.UpdateMyPageSettingsRequest;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 조회·설정 API. 프로필·CMA잔액·퍼즐판 평가·연동계좌·자동모으기 토글을 한 번에 제공한다.
 */
@RestController
@RequestMapping("/api/users/me/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    /** 마이페이지 집계 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyPage(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.ok("마이페이지 조회 성공", myPageService.getMyPage(userId)));
    }

    /** 자동 모으기 토글 부분 변경(변경분만). */
    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse<MyPageSettings>> updateSettings(
            @CurrentUserId Long userId,
            @RequestBody UpdateMyPageSettingsRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.ok("저장되었습니다.", myPageService.updateSettings(userId, request)));
    }
}
