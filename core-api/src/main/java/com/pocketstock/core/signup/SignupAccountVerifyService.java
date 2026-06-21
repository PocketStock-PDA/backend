package com.pocketstock.core.signup;

import com.pocketstock.core.signup.dto.AccountVerifyConfirmResponse;
import com.pocketstock.core.signup.dto.AccountVerifyRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 가입단계(로그인 전) 계좌 1원 인증 — 송금요청·확인.
 *
 * <p>은행 계좌 연동은 목데이터 영역이라 실제 1원 송금은 없다. 송금요청 시 3자리 코드를 생성해
 * 입금자명 {@code 포켓스톡###}을 구성하고 {@link SignupVerifyStore}에 verificationId로 저장한다.
 * 로그인 전이라 알림함·푸시 채널이 없어 코드는 응답으로 전달한다(mock). 확인은 코드 일치 여부만 반환하며
 * DB에 아무것도 쓰지 않는다(연동·영속화는 가입 완료 후 별도 흐름).
 */
@Service
@RequiredArgsConstructor
public class SignupAccountVerifyService {

    private final SignupVerifyStore store;
    private final SecureRandom random = new SecureRandom();

    private static final Duration TTL = Duration.ofSeconds(180);
    private static final String SENDER_NAME = "포켓스톡";

    /** 송금요청 — 3자리 코드 생성·세션 저장. mock이라 응답에 입금자명·코드를 노출한다. */
    public AccountVerifyRequestResponse request(Long accountId) {
        String code = String.format("%03d", random.nextInt(1000)); // 000~999
        String depositorName = SENDER_NAME + code;                 // 예: 포켓스톡482
        String verificationId = UUID.randomUUID().toString();

        store.save(verificationId, code, TTL);

        return new AccountVerifyRequestResponse(verificationId, depositorName, code, TTL.getSeconds());
    }

    /** 확인 — 입금자명 끝 3자리(code) 일치 여부. 성공 시 세션 1회 소비. */
    public AccountVerifyConfirmResponse confirm(String verificationId, String code) {
        return new AccountVerifyConfirmResponse(store.verify(verificationId, code));
    }
}
