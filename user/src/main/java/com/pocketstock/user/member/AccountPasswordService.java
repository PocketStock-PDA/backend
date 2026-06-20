package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.AccountPassword;
import com.pocketstock.user.member.dto.SetAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordResponse;
import com.pocketstock.user.member.mapper.AccountPasswordMapper;
import com.pocketstock.user.security.TxnAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 계좌(거래) 비밀번호 설정 및 거래 인증.
 * 거래 인증 성공 상태는 Redis에 30분 TTL로 저장하며, 주문/이체 등에서 이 키로 인증 여부를 확인한다.
 */
@Service
@RequiredArgsConstructor
public class AccountPasswordService {

    /** 계좌 비밀번호 형식 — 숫자 4자리. */
    private static final Pattern FORMAT = Pattern.compile("^\\d{4}$");

    private final AccountPasswordMapper accountPasswordMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    /** 계좌 비밀번호 설정/변경 — 인증 필요. */
    @Transactional
    public void set(Long userId, SetAccountPasswordRequest req) {
        requireAuth(userId);
        String raw = req == null ? null : req.accountPassword();
        if (!StringUtils.hasText(raw) || !FORMAT.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "계좌 비밀번호는 숫자 4자리여야 합니다.");
        }

        AccountPassword ap = AccountPassword.builder()
                .userId(userId)
                .passwordHash(passwordEncoder.encode(raw))
                .build();
        accountPasswordMapper.upsert(ap);
    }

    /**
     * 거래 인증 — 계좌 비밀번호 대조. "비밀번호 유지"(keepAuth) ON일 때만 30분 거래 세션을 기록한다.
     * OFF면 이번 1회만 통과하고 세션을 남기지 않아(키 미기록), 다음 거래에서 다시 비밀번호를 묻는다.
     */
    public VerifyAccountPasswordResponse verify(Long userId, VerifyAccountPasswordRequest req) {
        requireAuth(userId);
        String raw = req == null ? null : req.accountPassword();
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "계좌 비밀번호를 입력해 주세요.");
        }

        AccountPassword ap = accountPasswordMapper.findByUserId(userId);
        if (ap == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "계좌 비밀번호가 설정되어 있지 않습니다.");
        }
        if (Boolean.TRUE.equals(ap.getIsLocked())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "계좌 비밀번호가 잠겨 있습니다.");
        }
        if (!passwordEncoder.matches(raw, ap.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "계좌 비밀번호가 일치하지 않습니다.");
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        LocalDateTime expiresAt = verifiedAt;
        if (req.keepAuth()) {
            // "비밀번호 유지" ON: 30분 거래 세션 발급 → 이후 거래는 비밀번호 스킵
            expiresAt = verifiedAt.plus(TxnAuth.TTL);
            redis.opsForValue().set(TxnAuth.key(userId), verifiedAt.toString(), TxnAuth.TTL);
        }
        // OFF: 세션 미기록(expiresAt == verifiedAt) → 다음 거래에서 다시 비밀번호 입력
        return new VerifyAccountPasswordResponse(verifiedAt, expiresAt);
    }

    private void requireAuth(Long userId) {
        if (userId == null) {
            // JwtAuthFilter는 요청을 막지 않으므로(토큰 없으면 attribute 미설정) 여기서 인증을 강제한다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        }
    }
}
