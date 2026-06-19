package com.pocketstock.ledger.cma.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * CMA 계좌번호 암호화(AES-256-GCM). VARBINARY 컬럼(cma_accounts.account_no_enc)에 저장할 암호문을 만든다.
 * 저장 포맷: [12B IV][ciphertext+tag]. 키는 설정 시크릿의 SHA-256(32B).
 *
 * <p>trading {@code AccountNoCipher}와 동일 구조의 <b>의도적 복제</b> — cma→trading 도메인 의존을 피하기 위함.
 * 같은 설정값({@code account.cipher.secret})을 공유한다. 운영은 환경변수로 주입(커밋 금지).
 */
@Component
public class CmaAccountNoCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CmaAccountNoCipher(
            @Value("${account.cipher.secret}") String secret) {   // 기본값 없음 — 미주입 시 부팅 실패(fail-fast)
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    public byte[] encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("CMA 계좌번호 암호화 실패", e);
        }
    }

    public String decrypt(byte[] data) {
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("CMA 계좌번호 복호화 실패", e);
        }
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("키 파생 실패", e);
        }
    }
}
