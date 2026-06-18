package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.AuthMethod;
import com.pocketstock.user.member.domain.Member;
import com.pocketstock.user.member.dto.LoginRequest;
import com.pocketstock.user.member.dto.LoginResponse;
import com.pocketstock.user.member.dto.PinLoginRequest;
import com.pocketstock.user.member.dto.RefreshResponse;
import com.pocketstock.user.member.mapper.AuthMethodMapper;
import com.pocketstock.user.member.mapper.MemberMapper;
import com.pocketstock.user.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** PIN 로그인 시도 제한 — 윈도우 내 5회 실패 시 차단(ID/PW 로그인으로 폴백 유도). */
    private static final String PIN_FAIL_PREFIX = "pin-fail:";
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final Duration PIN_FAIL_WINDOW = Duration.ofMinutes(5);

    private final MemberMapper memberMapper;
    private final AuthMethodMapper authMethodMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redis;

    /** ID/PW 로그인 — 검증 성공 시 기기 등록 후 access/refresh 토큰 발급. */
    @Transactional   // 기기 등록(clear→update)을 한 트랜잭션으로 묶어 부분 실패를 방지
    public LoginResponse login(LoginRequest req, String deviceId) {
        if (!StringUtils.hasText(req.username()) || !StringUtils.hasText(req.password())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디와 비밀번호는 필수입니다.");
        }

        Member member = memberMapper.findByUsername(req.username());

        // 보안: '없는 아이디'와 '틀린 비번'을 구분하지 않는다(계정 존재 여부 노출 방지).
        if (member == null || !passwordEncoder.matches(req.password(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        registerDevice(member.getId(), deviceId);   // 이 기기를 사용자에 묶음(PIN 로그인 기반)

        String accessToken = jwtProvider.createToken(member.getId());
        String refreshToken = refreshTokenService.issue(member.getId());

        return new LoginResponse(accessToken, refreshToken, jwtProvider.getValiditySeconds());
    }

    /**
     * PIN/패턴 간편 로그인 — X-Device-Id로 사용자를 특정한 뒤 PIN/패턴을 대조한다.
     * deviceId는 신뢰 경계 밖(클라이언트 제공)이라 사용자 '특정'에만 쓰고, 인증은 PIN 대조로 한다.
     */
    public LoginResponse loginPin(PinLoginRequest req, String deviceId) {
        if (req == null || !StringUtils.hasText(req.type()) || !StringUtils.hasText(req.value())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type과 value는 필수입니다.");
        }
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "기기 정보가 없습니다. 아이디로 로그인해 주세요.");
        }

        Member member = memberMapper.findByDeviceId(deviceId);
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "등록되지 않은 기기입니다. 아이디로 로그인해 주세요.");
        }
        Long userId = member.getId();
        String failKey = PIN_FAIL_PREFIX + userId;

        // 무차별 대입 방어 — 윈도우 내 시도 초과 시 차단
        if (currentPinFailures(failKey) >= MAX_PIN_ATTEMPTS) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "간편 로그인 시도 횟수를 초과했습니다. 아이디로 로그인해 주세요.");
        }

        AuthMethod method = authMethodMapper.findByUserIdAndType(userId, req.type().toUpperCase());
        // 미설정/불일치를 구분하지 않는다(어떤 인증수단이 설정됐는지 노출 방지).
        if (method == null || !passwordEncoder.matches(req.value(), method.getSecretHash())) {
            recordPinFailure(failKey);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "간편 인증 정보가 올바르지 않습니다.");
        }

        redis.delete(failKey);   // 성공 → 실패 카운터 리셋

        String accessToken = jwtProvider.createToken(userId);
        String refreshToken = refreshTokenService.issue(userId);
        return new LoginResponse(accessToken, refreshToken, jwtProvider.getValiditySeconds());
    }

    /** deviceId를 로그인 사용자에 등록. 기존 소유자에서 분리 후 부여(기기 1대=계정 1명). */
    private void registerDevice(Long userId, String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        memberMapper.clearDeviceId(deviceId);
        memberMapper.updateDeviceId(userId, deviceId);
    }

    /** 현재 PIN 실패 횟수 — 값이 비었거나 손상되면 0으로 간주(파싱 예외로 인증 API가 죽지 않게). */
    private int currentPinFailures(String failKey) {
        String cnt = redis.opsForValue().get(failKey);
        if (cnt == null) {
            return 0;
        }
        try {
            return Integer.parseInt(cnt);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** PIN 실패 누적 — 첫 실패에 윈도우 TTL을 건다. */
    private void recordPinFailure(String failKey) {
        Long fails = redis.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redis.expire(failKey, PIN_FAIL_WINDOW);
        }
    }

    /** 토큰 재발급 — refreshToken이 Redis에 유효하면 새 accessToken 발급. */
    public RefreshResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "refreshToken은 필수입니다.");
        }

        Long userId = refreshTokenService.findUserId(refreshToken);
        if (userId == null) {
            // 없거나 만료된 토큰 → 재로그인 필요
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token입니다.");
        }

        String accessToken = jwtProvider.createToken(userId);
        return new RefreshResponse(accessToken, jwtProvider.getValiditySeconds());
    }

    /** 로그아웃 — refreshToken 폐기(멱등: 없어도 성공 취급). */
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revoke(refreshToken);
        }
    }
}
