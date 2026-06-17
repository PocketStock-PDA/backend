package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.Member;
import com.pocketstock.user.member.dto.LoginRequest;
import com.pocketstock.user.member.dto.LoginResponse;
import com.pocketstock.user.member.dto.RefreshResponse;
import com.pocketstock.user.member.mapper.MemberMapper;
import com.pocketstock.user.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    /** ID/PW 로그인 — 검증 성공 시 access/refresh 토큰 발급. */
    public LoginResponse login(LoginRequest req) {
        if (!StringUtils.hasText(req.username()) || !StringUtils.hasText(req.password())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디와 비밀번호는 필수입니다.");
        }

        Member member = memberMapper.findByUsername(req.username());

        // 보안: '없는 아이디'와 '틀린 비번'을 구분하지 않는다(계정 존재 여부 노출 방지).
        if (member == null || !passwordEncoder.matches(req.password(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtProvider.createToken(member.getId());
        String refreshToken = refreshTokenService.issue(member.getId());

        return new LoginResponse(accessToken, refreshToken, jwtProvider.getValiditySeconds());
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
