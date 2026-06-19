package com.pocketstock.core.notification;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.notification.dto.MarkAllReadResponse;
import com.pocketstock.core.notification.dto.MarkReadResponse;
import com.pocketstock.core.notification.dto.NotificationListResponse;
import com.pocketstock.core.notification.dto.NotificationSettingsRequest;
import com.pocketstock.core.notification.dto.NotificationSettingsResponse;
import com.pocketstock.core.notification.dto.PushTokenRequest;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        NotificationListResponse data = notificationService.getNotifications(userId, read, page, size);
        return ResponseEntity.ok(ApiResponse.ok("알림 목록 조회 성공", data));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<MarkReadResponse>> markRead(
            @CurrentUserId Long userId,
            @PathVariable Long id) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MarkReadResponse data = notificationService.markRead(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("알림 읽음 처리 성공", data));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(
            @CurrentUserId Long userId) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MarkAllReadResponse data = notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.ok("알림 전체 읽음 처리 성공", data));
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushTokenRequest request) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        notificationService.registerToken(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("푸시 토큰 등록 성공", null));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateSettings(
            @CurrentUserId Long userId,
            @Valid @RequestBody NotificationSettingsRequest request) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        NotificationSettingsResponse data = notificationService.updateSettings(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("알림 수신 설정 완료", data));
    }
}
