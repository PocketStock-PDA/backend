package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.VerificationConfirmRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 후 assets 1원 인증의 코드 자리수가 가입단계와 3자리로 통일됐는지 보장하는 회귀 테스트.
 */
class BankVerificationUnificationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void codeDigits_is3() {
        assertThat(BankVerification.CODE_DIGITS).isEqualTo(3);
    }

    @Test
    void confirmRequest_accepts3Digits() {
        assertThat(validator.validate(new VerificationConfirmRequest("482"))).isEmpty();
    }

    @Test
    void confirmRequest_rejects4DigitsAndOthers() {
        assertThat(validator.validate(new VerificationConfirmRequest("1234"))).isNotEmpty(); // 4자리 거부
        assertThat(validator.validate(new VerificationConfirmRequest("48"))).isNotEmpty();   // 2자리 거부
        assertThat(validator.validate(new VerificationConfirmRequest("abc"))).isNotEmpty();  // 비숫자 거부
        assertThat(validator.validate(new VerificationConfirmRequest(""))).isNotEmpty();     // 공백 거부
    }
}
