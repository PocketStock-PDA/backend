package com.pocketstock.ledger.cma.service;

import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.dto.response.CmaAccountResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.support.CmaAccountNoCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CMA 계좌 개설 — 서비스 진입 게이트 계좌.
 *
 * <p>온보딩의 마지막 단계로, 본인인증·약관·계좌비밀번호(회원 도메인 소관)가 끝난 뒤 호출된다.
 * 본 서비스는 인증/비번을 다루지 않고 <b>계좌 + 통화 지갑 레코드만 멱등 생성</b>한다.
 * 종합계좌 개설(trading)과는 분리된 별개 계좌이며, 사용자당 1행이다.
 */
@Service
@RequiredArgsConstructor
public class CmaAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CMA_SUFFIX = "90";                       // 종합(01/02)과 구분되는 CMA 접미사
    private static final String KRW = "KRW";
    private static final BigDecimal KRW_INTEREST_RATE = new BigDecimal("0.0350");  // 연 3.5% (가입 화면 기준)

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaAccountNoCipher cipher;

    /**
     * CMA 계좌 개설(멱등). 이미 있으면 기존 계좌를 반환하고, 없으면 계좌 + 원화 지갑(0원, 3.5%)을 생성한다.
     * 달러 지갑은 첫 환전 때 생성한다(여기서는 만들지 않음).
     */
    @Transactional
    public CmaAccountResponse openOrGet(Long userId) {
        CmaAccount existing = accountMapper.findByUserId(userId);
        if (existing != null) {
            return toResponse(existing);
        }
        try {
            CmaAccount account = createAccount(userId);
            seedKrwWallet(account.getId());
            return toResponse(account);
        } catch (DuplicateKeyException e) {
            // 동시 첫 호출 경합 — 다른 트랜잭션이 먼저 생성한 계좌를 재조회해 반환(멱등 보장)
            return toResponse(accountMapper.findByUserId(userId));
        }
    }

    private CmaAccount createAccount(Long userId) {
        CmaAccount account = new CmaAccount();
        account.setUserId(userId);
        account.setAccountNoEnc(cipher.encrypt(generateAccountNo()));
        account.setStatus(STATUS_ACTIVE);
        account.setOpenedAt(LocalDateTime.now());
        accountMapper.insert(account);   // useGeneratedKeys → id 채움
        return account;
    }

    private void seedKrwWallet(Long cmaAccountId) {
        CmaBalance krw = new CmaBalance();
        krw.setCmaAccountId(cmaAccountId);
        krw.setCurrency(KRW);
        krw.setBalance(BigDecimal.ZERO);
        krw.setInterestRate(KRW_INTEREST_RATE);
        balanceMapper.insertBalance(krw);
    }

    /** 5자리 베이스 + CMA 접미사 (예: 98765-90) */
    private String generateAccountNo() {
        String base = String.format("%05d", ThreadLocalRandom.current().nextInt(10000, 100000));
        return base + "-" + CMA_SUFFIX;
    }

    private CmaAccountResponse toResponse(CmaAccount account) {
        List<CmaAccountResponse.BalanceItem> balances = balanceMapper.findByAccountId(account.getId()).stream()
                .map(b -> new CmaAccountResponse.BalanceItem(b.getCurrency(), b.getBalance(), b.getInterestRate()))
                .toList();
        return new CmaAccountResponse(
                cipher.decrypt(account.getAccountNoEnc()),
                account.getOpenedAt(),
                balances
        );
    }
}
