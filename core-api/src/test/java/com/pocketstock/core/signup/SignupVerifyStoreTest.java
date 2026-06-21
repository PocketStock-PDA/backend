package com.pocketstock.core.signup;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SignupVerifyStoreTest {

    private final SignupVerifyStore store = new SignupVerifyStore();

    @Test
    void verify_success_thenConsumed() {
        store.save("v1", "482", Duration.ofMinutes(3));

        assertThat(store.verify("v1", "482")).isTrue();   // 성공
        assertThat(store.verify("v1", "482")).isFalse();  // 1회 소비 → 재검증 불가
    }

    @Test
    void verify_wrongCode_keepsSession_allowsRetry() {
        store.save("v2", "482", Duration.ofMinutes(3));

        assertThat(store.verify("v2", "000")).isFalse();  // 불일치 — 세션 유지
        assertThat(store.verify("v2", "482")).isTrue();   // TTL 내 재시도 성공
    }

    @Test
    void verify_unknownId_false() {
        assertThat(store.verify("nope", "482")).isFalse();
    }

    @Test
    void verify_expired_false() throws InterruptedException {
        store.save("v3", "482", Duration.ofMillis(10));
        Thread.sleep(30);

        assertThat(store.verify("v3", "482")).isFalse();  // 만료
    }

    @Test
    void save_purgesExpiredEntries_preventsUnboundedGrowth() throws InterruptedException {
        store.save("old1", "111", Duration.ofMillis(10));
        store.save("old2", "222", Duration.ofMillis(10));
        Thread.sleep(30);                                  // old1·old2 만료

        store.save("fresh", "333", Duration.ofMinutes(3)); // save 시 만료분 일괄 정리

        assertThat(store.size()).isEqualTo(1);             // confirm 없이 쌓인 만료분 제거 → fresh만 남음
    }
}
