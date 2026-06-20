package com.pocketstock.user.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 휴대폰 본인확인(난수문자·SMS) 임시 인증코드 인메모리 스토어.
 * - 난수문자: requestId → randomCode
 * - SMS: phone → code
 * TTL은 조회 시점에 lazy 만료(별도 스케줄러 없이 만료 항목은 get 시 제거).
 * 검증 성공 시 일회성 소비(재사용 방지).
 *
 * 단기(가입 직전) 세션이라 서버 재시작·멀티인스턴스 영향은 미미하다고 보고 인메모리로 둔다.
 */
@Component
public class VerificationStore {

    private record Entry(String code, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, Entry> certSessions = new ConcurrentHashMap<>(); // requestId → randomCode
    private final Map<String, Entry> smsCodes = new ConcurrentHashMap<>();      // phone → code

    public void saveCert(String requestId, String randomCode, Duration ttl) {
        certSessions.put(requestId, new Entry(randomCode, Instant.now().plus(ttl)));
    }

    /** 일치 + 미만료면 true 후 세션 소비. 만료 항목은 조회 시 제거. */
    public boolean verifyCert(String requestId, String randomCode) {
        return matchAndConsume(certSessions, requestId, randomCode);
    }

    public void saveSms(String phone, String code, Duration ttl) {
        smsCodes.put(phone, new Entry(code, Instant.now().plus(ttl)));
    }

    public boolean verifySms(String phone, String code) {
        return matchAndConsume(smsCodes, phone, code);
    }

    private boolean matchAndConsume(Map<String, Entry> store, String key, String code) {
        Entry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(key, entry);     // 만료 — 그 항목만 정리(새로 저장된 항목 보호)
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;                 // 불일치 — 세션 유지(TTL 내 재시도 허용)
        }
        return store.remove(key, entry);  // 성공 — 읽어둔 항목만 원자적 제거(동시 검증 시 1회만 true)
    }
}
