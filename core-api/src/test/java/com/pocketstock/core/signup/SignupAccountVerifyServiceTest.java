package com.pocketstock.core.signup;

import com.pocketstock.core.signup.dto.AccountVerifyRequestResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignupAccountVerifyServiceTest {

    private final SignupAccountVerifyService service = new SignupAccountVerifyService(new SignupVerifyStore());

    @Test
    void request_generates3DigitCode_andMatchingDepositorName() {
        AccountVerifyRequestResponse res = service.request(1L);

        assertThat(res.verificationId()).isNotBlank();
        assertThat(res.code()).matches("\\d{3}");                       // 3자리
        assertThat(res.depositorName()).isEqualTo("포켓스톡" + res.code()); // 포켓스톡###
        assertThat(res.expiresIn()).isEqualTo(180);
    }

    @Test
    void confirm_correctCode_verifiedTrue_thenConsumed() {
        AccountVerifyRequestResponse res = service.request(1L);

        assertThat(service.confirm(res.verificationId(), res.code()).verified()).isTrue();
        assertThat(service.confirm(res.verificationId(), res.code()).verified()).isFalse(); // 1회 소비
    }

    @Test
    void confirm_wrongCode_verifiedFalse() {
        AccountVerifyRequestResponse res = service.request(1L);
        String wrong = res.code().equals("000") ? "999" : "000";

        assertThat(service.confirm(res.verificationId(), wrong).verified()).isFalse();
    }
}
