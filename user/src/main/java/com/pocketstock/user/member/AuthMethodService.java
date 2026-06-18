package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.AuthMethod;
import com.pocketstock.user.member.dto.SetAuthMethodRequest;
import com.pocketstock.user.member.mapper.AuthMethodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 간편인증(PIN/패턴) 설정.
 * 보안: 형식 검증 + 취약값 거부 + BCrypt 해싱(평문 저장 금지). 인증된 사용자만.
 */
@Service
@RequiredArgsConstructor
public class AuthMethodService {

    private static final String PIN = "PIN";
    private static final String PATTERN = "PATTERN";

    private static final Pattern PIN_FORMAT = Pattern.compile("^\\d{6}$");   // PIN 숫자 6자리
    private static final int PATTERN_MIN_NODES = 4;                          // 패턴 최소 연결점

    private final AuthMethodMapper authMethodMapper;
    private final PasswordEncoder passwordEncoder;

    /** PIN/패턴 설정·변경 — 인증 필요. */
    @Transactional
    public void set(Long userId, SetAuthMethodRequest req) {
        if (userId == null) {
            // JwtAuthFilter는 요청을 막지 않으므로(토큰 없으면 attribute 미설정) 여기서 인증을 강제한다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        }
        if (req == null || !StringUtils.hasText(req.type()) || !StringUtils.hasText(req.value())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type과 value는 필수입니다.");
        }

        String type = req.type().toUpperCase();
        String value = req.value();
        validate(type, value);

        AuthMethod method = AuthMethod.builder()
                .userId(userId)
                .methodType(type)
                .secretHash(passwordEncoder.encode(value))   // 평문 저장 금지 — BCrypt
                .build();
        authMethodMapper.upsert(method);
    }

    private void validate(String type, String value) {
        switch (type) {
            case PIN -> {
                if (!PIN_FORMAT.matcher(value).matches()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "PIN은 숫자 6자리여야 합니다.");
                }
                if (isWeakPin(value)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "너무 단순한 PIN은 사용할 수 없습니다.");
                }
            }
            case PATTERN -> {
                // 패턴 값은 연결점 시퀀스 문자열(구분자 포함 가능) — 최소 연결점 수만 보장
                long nodes = value.chars().filter(Character::isLetterOrDigit).count();
                if (nodes < PATTERN_MIN_NODES) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "패턴은 최소 " + PATTERN_MIN_NODES + "개 점을 연결해야 합니다.");
                }
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 인증 방식입니다: " + type);
        }
    }

    /** 취약 PIN: 모두 같은 숫자(000000) 또는 연속 증가·감소(123456/654321). */
    private boolean isWeakPin(String pin) {
        boolean allSame = true;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int prev = pin.charAt(i - 1) - '0';
            int cur = pin.charAt(i) - '0';
            if (cur != prev) allSame = false;
            if (cur != prev + 1) ascending = false;
            if (cur != prev - 1) descending = false;
        }
        return allSame || ascending || descending;
    }
}
