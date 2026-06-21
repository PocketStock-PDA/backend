package com.pocketstock.core.signup;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가입단계 1원 인증 코드 인메모리 스토어(휘발성). {@code verificationId → code}.
 *
 * <p>로그인 전 단계라 userId·DB에 묶이지 않는 순수 mock 세션이다. TTL은 조회 시점에 lazy 만료하고,
 * 검증 성공 시 일회성 소비(재사용 방지)한다. 가입 직전 단기 세션이라 서버 재시작·멀티인스턴스 영향은
 * 미미하다고 보고 인메모리로 둔다(user 도메인 VerificationStore와 동일한 판단, 코드 결합은 피함).
 */
@Component
public class SignupVerifyStore {

    private record Entry(String code, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>(); // verificationId → code

    public void save(String verificationId, String code, Duration ttl) {
        store.put(verificationId, new Entry(code, Instant.now().plus(ttl)));
    }

    /** 일치 + 미만료면 true 후 세션 소비. 만료 항목은 조회 시 제거, 불일치는 유지(TTL 내 재시도 허용). */
    public boolean verify(String verificationId, String code) {
        Entry entry = store.get(verificationId);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(verificationId, entry);  // 만료 — 그 항목만 정리
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;                         // 불일치 — 세션 유지
        }
        return store.remove(verificationId, entry); // 성공 — 읽어둔 항목만 원자적 제거(동시 검증 시 1회만 true)
    }
}
