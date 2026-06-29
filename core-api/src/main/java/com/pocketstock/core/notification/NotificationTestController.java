package com.pocketstock.core.notification;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.core.notification.dto.NotificationSettingRow;
import com.pocketstock.core.notification.dto.PushTestResponse;
import com.pocketstock.core.notification.mapper.NotificationSettingMapper;
import com.pocketstock.core.notification.push.PushResult;
import com.pocketstock.core.notification.push.PushSender;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 푸시 검수 엔드포인트 — 요청 본문 JSON을 가공 없이 그대로 호출자 본인의 등록 구독으로 1건 발송한다.
 * 맥에서 curl로 타입별 payload를 쏴 폰에서 개별 확인하는 용도(FE 서비스워커가 렌더, 백엔드는 전달만).
 *
 * <p>{@code webpush.test-endpoint.enabled=true}일 때만 빈으로 등록된다(기본 비활성 → 운영 차단).
 * 발송 대상은 {@link CurrentUserId} 본인이 등록한 구독으로 한정 — 타 유저 발송 불가.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webpush.test-endpoint.enabled", havingValue = "true")
public class NotificationTestController {

    private final NotificationSettingMapper notificationSettingMapper;
    private final PushSender pushSender;

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<PushTestResponse>> sendTest(
            @CurrentUserId Long userId,
            @RequestBody String rawJsonPayload) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 본인 등록 구독으로만 발송 — 토큰 없거나 WEB 구독 아니면 404.
        NotificationSettingRow setting = notificationSettingMapper.findByUserId(userId);
        String token = setting == null ? null : setting.getPushToken();
        if (token == null || token.isBlank() || !"WEB".equalsIgnoreCase(setting.getPlatform())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "등록된 웹푸시 구독이 없습니다");
        }

        PushResult result = pushSender.sendRaw(token, rawJsonPayload);   // 가공 없이 그대로 전달
        if (result == PushResult.EXPIRED) {
            try {
                notificationSettingMapper.clearToken(userId);            // 만료 구독 정리 — best-effort
            } catch (Exception e) {
                log.warn("만료 구독 토큰 정리 실패(userId={}): {}", userId, e.getMessage());
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "푸시 구독이 만료되어 정리했습니다 — 재등록 필요");
        }

        int sent = result == PushResult.SENT ? 1 : 0;
        return ResponseEntity.ok(ApiResponse.ok("테스트 푸시 발송", new PushTestResponse(sent, result.name())));
    }
}
