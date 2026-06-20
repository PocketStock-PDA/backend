package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.asset.dto.VerificationConfirmResponse;
import com.pocketstock.core.asset.dto.VerificationRequestResponse;
import com.pocketstock.core.asset.mapper.BankAccountMapper;
import com.pocketstock.core.notification.NotificationService;
import com.pocketstock.core.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 계좌 1원 인증(소유권 확인) — 송금요청·확인.
 *
 * <p>은행 계좌 연동은 목데이터 영역이라 실제 자금 이동(1원 송금)은 없다. 송금요청 시 코드를 생성해
 * Redis 챌린지로 저장하고, 웹푸시로 "1원 입금" 목 알림을 보내 사용자가 코드를 입력하게 한다(B3).
 * 확인 성공 시 {@code linked_bank_accounts.is_verified}만 마킹한다 — CMA(DB-B) 원장과 무관.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankVerificationService {

    private final BankAccountMapper bankAccountMapper;
    private final StringRedisTemplate redis;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();

    /**
     * 송금요청 — 코드 생성·챌린지 저장·목 푸시 발송.
     * 트랜잭션 없음(DB 쓰기 없이 Redis + 푸시만). 미소유 404, 이미인증 409.
     */
    public VerificationRequestResponse request(Long userId, Long accountId) {
        requireUnverifiedOwnAccount(userId, accountId);

        String code = generateCode();
        String key = BankVerification.key(userId, accountId);

        // 재요청 시 기존 챌린지 덮어쓰기(코드 갱신 + 시도수 0 리셋).
        redis.delete(key);
        redis.opsForHash().put(key, BankVerification.FIELD_CODE, code);
        redis.opsForHash().put(key, BankVerification.FIELD_ATTEMPTS, "0");
        redis.expire(key, BankVerification.TTL);

        sendMockDepositPush(userId, code);
        // 로컬 디버깅 fallback(목 데이터·DEBUG 전용). 운영에선 com.pocketstock 로그레벨로 차단.
        log.debug("[1원인증] 목 코드 발송 userId={} accountId={} code={}", userId, accountId, code);

        return new VerificationRequestResponse(
                accountId,
                BankVerification.SENDER_NAME,
                BankVerification.TTL.getSeconds(),
                BankVerification.MAX_ATTEMPTS
        );
    }

    /**
     * 확인 — 코드 검증 후 성공 시 인증 마킹.
     * 만료/없음 400, 코드불일치 400, 시도초과 429, 미소유 404, 이미인증 409.
     */
    @Transactional
    public VerificationConfirmResponse confirm(Long userId, Long accountId, String inputCode) {
        requireUnverifiedOwnAccount(userId, accountId);

        String key = BankVerification.key(userId, accountId);
        Object stored = redis.opsForHash().get(key, BankVerification.FIELD_CODE);
        if (stored == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }

        if (!stored.toString().equals(inputCode)) {
            long attempts = redis.opsForHash().increment(key, BankVerification.FIELD_ATTEMPTS, 1);
            if (attempts >= BankVerification.MAX_ATTEMPTS) {
                redis.delete(key);   // 한도 초과 → 챌린지 폐기(재요청 필요)
                throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        // 일치 → 1회용 코드 즉시 소비 + 인증 마킹.
        redis.delete(key);
        int updated = bankAccountMapper.markVerified(userId, accountId);
        if (updated == 0) {
            // 선검사 통과 후 동시 요청 등으로 이미 인증된 경우 — 멱등 충돌로 처리.
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }

        return new VerificationConfirmResponse(accountId, true);
    }

    /** 본인 소유 + 미인증 계좌인지 확인. 없음/미소유 404, 이미인증 409. */
    private void requireUnverifiedOwnAccount(Long userId, Long accountId) {
        Boolean verified = bankAccountMapper.findVerifyStatus(userId, accountId);
        if (verified == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "계좌를 찾을 수 없습니다.");
        }
        if (verified) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, BankVerification.CODE_DIGITS);   // 4자리 → 10000
        return String.format("%0" + BankVerification.CODE_DIGITS + "d", random.nextInt(bound));
    }

    /** 목 1원 입금 알림 — 입금자명에 코드를 실어 보낸다(은행 거래내역 시뮬). 발송 실패는 무시(best-effort). */
    private void sendMockDepositPush(Long userId, String code) {
        String title = "1원이 입금되었어요";
        String body = String.format("입금자명 [%s-%s] 확인 후 인증 코드 %s 를 %d분 안에 입력해 주세요.",
                BankVerification.SENDER_NAME, code, code, BankVerification.TTL.toMinutes());
        notificationService.create(userId, NotificationType.ACCOUNT_VERIFY, title, body);
    }
}
