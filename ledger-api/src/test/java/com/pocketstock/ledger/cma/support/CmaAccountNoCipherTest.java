package com.pocketstock.ledger.cma.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CmaAccountNoCipher} 단위 테스트 — 왕복 암복호화 + null-safe decrypt.
 * 시드/레거시 계좌의 {@code account_no_enc=NULL}이 복호화에서 터지지 않고 null로 처리되는지 검증.
 */
class CmaAccountNoCipherTest {

    private final CmaAccountNoCipher cipher = new CmaAccountNoCipher("test-secret-for-unit");

    @Test
    @DisplayName("암호화→복호화 왕복이 원문과 일치한다")
    void encryptDecryptRoundTrip() {
        String plain = "1234567890-90";
        assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("account_no_enc가 NULL이면 복호화는 null 반환(예외 없음)")
    void decryptNullReturnsNull() {
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("account_no_enc가 빈 바이트면 복호화는 null 반환(예외 없음)")
    void decryptEmptyReturnsNull() {
        assertThat(cipher.decrypt(new byte[0])).isNull();
    }
}
