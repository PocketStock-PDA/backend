package com.pocketstock.user.notification;

/**
 * 사용자에게 웹푸시를 보내는 포트(헥사고날). 구현(VAPID 등)은 실행 앱(core-api)이 어댑터로 제공.
 *
 * <p>user 모듈은 web-push 의존성·push_token(DB A)을 모르므로 인터페이스만 의존하고,
 * 런타임에 core-api의 {@code WebPushNotifier}가 주입된다. (ledger-api는 이 빈을 스캔하지 않음)
 */
public interface PushNotifier {

    /**
     * 해당 사용자의 등록된 푸시 구독으로 알림을 발송한다.
     * 알림함에 기록하지 않고 토글 설정도 무시한다(잠금 해제 인증번호 등 1회성 보안 알림 전용).
     *
     * @return 실제 발송됐으면 true. 미구독·만료·발송 실패 시 false(예외를 던지지 않음).
     */
    boolean sendToUser(Long userId, String title, String body);
}
