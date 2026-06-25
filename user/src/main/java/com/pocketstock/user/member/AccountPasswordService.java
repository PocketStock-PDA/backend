package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.auth.PhoneVerificationService;
import com.pocketstock.user.auth.dto.SmsVerifyRequest;
import com.pocketstock.user.member.domain.AccountPassword;
import com.pocketstock.user.member.domain.Member;
import com.pocketstock.user.member.dto.SetAccountPasswordRequest;
import com.pocketstock.user.member.dto.UnlockAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordRequest;
import com.pocketstock.user.member.dto.VerifyAccountPasswordResponse;
import com.pocketstock.user.member.mapper.AccountPasswordMapper;
import com.pocketstock.user.member.mapper.MemberMapper;
import com.pocketstock.user.notification.PushNotifier;
import com.pocketstock.user.security.TxnAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 계좌(거래) 비밀번호 설정 및 거래 인증.
 * 거래 인증 성공 상태는 Redis에 30분 TTL로 저장하며, 주문/이체 등에서 이 키로 인증 여부를 확인한다.
 *
 * <p>오답 5회면 영구 잠금(DB is_locked)되며, 해제는 등록 휴대폰으로 푸시 발송된 인증번호로만 가능하다.
 * 잠금 카운터는 Redis에 두되 PIN 로그인과 달리 자동만료 TTL을 걸지 않는다(영구 잠금).
 */
@Service
@RequiredArgsConstructor
public class AccountPasswordService {

    /** 계좌 비밀번호 형식 — 숫자 4자리. */
    private static final Pattern FORMAT = Pattern.compile("^\\d{4}$");

    /** 오답 누적 카운터 키 접두사 — acctpw:fail:{userId}. TTL 없음(영구 잠금). */
    private static final String FAIL_PREFIX = "acctpw:fail:";
    /** 자동 잠금까지 허용하는 오답 횟수. */
    private static final int MAX_ATTEMPTS = 5;

    private final AccountPasswordMapper accountPasswordMapper;
    private final MemberMapper memberMapper;
    private final PhoneVerificationService phoneVerificationService;
    private final PushNotifier pushNotifier;
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
            throw new BusinessException(ErrorCode.ACCOUNT_PASSWORD_LOCKED);
        }
        if (!passwordEncoder.matches(raw, ap.getPasswordHash())) {
            long fails = increment(userId);
            if (fails >= MAX_ATTEMPTS) {
                accountPasswordMapper.updateLockStatus(userId, true);   // 영구 잠금
                redis.delete(failKey(userId));                          // 카운터 정리(잠금이 상태원)
                throw new BusinessException(ErrorCode.ACCOUNT_PASSWORD_LOCKED);
            }
            long remaining = MAX_ATTEMPTS - fails;
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "계좌 비밀번호가 일치하지 않습니다. (남은 횟수 " + remaining + "회)");
        }

        redis.delete(failKey(userId));   // 정답 → 실패 카운터 리셋

        // 검증 성공 시 항상 인증 키 발급. 토글에 따라 값/TTL만 다르게 둔다.
        //  ON  → KEEP(30분 유지, 여러 거래 통과)  /  OFF → ONCE(직후 1건만, 가드가 소비)
        LocalDateTime verifiedAt = LocalDateTime.now();
        String value = req.keepAuth() ? TxnAuth.VALUE_KEEP : TxnAuth.VALUE_ONCE;
        Duration ttl = req.keepAuth() ? TxnAuth.TTL : TxnAuth.ONCE_TTL;
        redis.opsForValue().set(TxnAuth.key(userId), value, ttl);

        LocalDateTime expiresAt = verifiedAt.plus(ttl);
        return new VerifyAccountPasswordResponse(verifiedAt, expiresAt);
    }

    /**
     * 잠금 해제 인증번호 발송 — 잠겨 있을 때만, 등록 휴대폰으로 푸시 전송.
     * SMS가 아닌 푸시로 보내며(코드는 응답에 노출하지 않음), 푸시 미구독이면 발송할 수단이 없어 실패한다.
     */
    public void sendUnlockCode(Long userId) {
        requireAuth(userId);

        AccountPassword ap = accountPasswordMapper.findByUserId(userId);
        if (ap == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "계좌 비밀번호가 설정되어 있지 않습니다.");
        }
        if (!Boolean.TRUE.equals(ap.getIsLocked())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잠금 상태가 아닙니다.");
        }

        Member member = memberMapper.findById(userId);
        if (member == null || !StringUtils.hasText(member.getPhone())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "등록된 휴대폰 번호가 없습니다.");
        }

        String code = phoneVerificationService.issueSmsCode(member.getPhone());
        boolean sent = pushNotifier.sendToUser(userId,
                "포켓스톡 계좌 잠금 해제",
                "인증번호 [" + code + "]를 입력해 잠금을 해제하세요. (3분 내 유효)");
        if (!sent) {
            throw new BusinessException(ErrorCode.PUSH_NOT_AVAILABLE);
        }
    }

    /**
     * 잠금 해제 — 발송된 인증번호 검증 성공 시 is_locked 해제 + 실패 카운터 리셋.
     * 휴대폰 번호는 서버가 로그인 사용자(member.phone)로 결정한다(클라이언트는 code만 전달).
     */
    @Transactional
    public void unlock(Long userId, UnlockAccountPasswordRequest req) {
        requireAuth(userId);
        String code = req == null ? null : req.code();
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증번호를 입력해 주세요.");
        }

        AccountPassword ap = accountPasswordMapper.findByUserId(userId);
        if (ap == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "계좌 비밀번호가 설정되어 있지 않습니다.");
        }
        if (!Boolean.TRUE.equals(ap.getIsLocked())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잠금 상태가 아닙니다.");
        }

        Member member = memberMapper.findById(userId);
        if (member == null || !StringUtils.hasText(member.getPhone())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "등록된 휴대폰 번호가 없습니다.");
        }

        boolean verified = phoneVerificationService
                .verifySms(new SmsVerifyRequest(member.getPhone(), code))
                .verified();
        if (!verified) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증번호가 일치하지 않거나 만료되었습니다.");
        }

        accountPasswordMapper.updateLockStatus(userId, false);
        redis.delete(failKey(userId));   // 해제 → 실패 카운터 리셋
    }

    private void requireAuth(Long userId) {
        if (userId == null) {
            // JwtAuthFilter는 요청을 막지 않으므로(토큰 없으면 attribute 미설정) 여기서 인증을 강제한다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        }
    }

    private String failKey(Long userId) {
        return FAIL_PREFIX + userId;
    }

    /** 오답 누적(+1) 후 현재 횟수 반환. PIN 로그인과 달리 TTL을 걸지 않는다(영구 잠금). */
    private long increment(Long userId) {
        Long fails = redis.opsForValue().increment(failKey(userId));
        return fails == null ? MAX_ATTEMPTS : fails;   // 카운터 손상 시 보수적으로 잠금 유도
    }
}
