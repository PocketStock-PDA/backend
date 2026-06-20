package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.dto.AccountStatusResponse;
import com.pocketstock.ledger.trading.dto.DepositResponse;
import com.pocketstock.ledger.trading.dto.OpenAccountRequest;
import com.pocketstock.ledger.trading.dto.OpenAccountResponse;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.support.AccountNoCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SecuritiesAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 위탁계좌 시장 → 계좌번호 상품코드 접미사 */
    private static final Map<String, String> MARKET_SUFFIX = Map.of(
            "DOMESTIC", "01",
            "OVERSEAS", "02"
    );

    /** 위탁계좌 시장 → 예수금 통화 (DOMESTIC=KRW / OVERSEAS=USD) */
    private static final Map<String, String> MARKET_CURRENCY = Map.of(
            "DOMESTIC", "KRW",
            "OVERSEAS", "USD"
    );

    private final SecuritiesAccountMapper accountMapper;
    private final DepositMapper depositMapper;
    private final AccountNoCipher cipher;

    /** 증권계좌 개설 — 요청 시장 중 미개설분만 생성, 기존 계좌번호 베이스 재사용. */
    @Transactional
    public OpenAccountResponse open(Long userId, OpenAccountRequest request) {
        requireAuth(userId);

        List<String> requested = normalizeTypes(request);

        List<SecuritiesAccount> existing = accountMapper.findByUserId(userId);
        String base = existing.isEmpty() ? generateBase() : baseOf(existing.get(0));

        for (String market : requested) {
            if (accountMapper.existsByUserIdAndMarket(userId, market)) {
                continue;
            }
            String accountNo = base + "-" + MARKET_SUFFIX.get(market);
            SecuritiesAccount account = SecuritiesAccount.builder()
                    .userId(userId)
                    .market(market)
                    .accountNoEnc(cipher.encrypt(accountNo))
                    .status(STATUS_ACTIVE)
                    .isFractionalEnabled(true)
                    .openedAt(LocalDateTime.now())
                    .build();
            accountMapper.insert(account);
            // 예수금 잔액행을 계좌와 짝으로 생성(balance=0) → 이후 입출금은 항상 UPDATE만.
            depositMapper.insertBalance(account.getId(), MARKET_CURRENCY.get(market));
        }

        String representative = base + "-" + MARKET_SUFFIX.get(requested.get(0));
        return new OpenAccountResponse(representative, requested);
    }

    /** 계좌 상태 조회 */
    @Transactional(readOnly = true)
    public List<AccountStatusResponse> getAccounts(Long userId) {
        requireAuth(userId);
        List<AccountStatusResponse> result = new ArrayList<>();
        for (SecuritiesAccount account : accountMapper.findByUserId(userId)) {
            result.add(new AccountStatusResponse(
                    account.getMarket(),
                    cipher.decrypt(account.getAccountNoEnc()),
                    account.getStatus()));
        }
        return result;
    }

    /** 예수금/출금가능/주문가능 조회 (KRW = 국내 위탁계좌) */
    @Transactional(readOnly = true)
    public DepositResponse getDeposit(Long userId) {
        requireAuth(userId);
        SecuritiesAccount domestic = accountMapper.findByUserIdAndMarket(userId, "DOMESTIC");
        BigDecimal balance = (domestic == null) ? null : depositMapper.findBalanceByAccount(domestic.getId());
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        // 자금 유입·미체결 증거금 흐름 구현 전까지 동일값
        return new DepositResponse(balance, balance, balance);
    }

    // ---- helpers ----

    private void requireAuth(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 중복 제거 + 허용값 검증(DOMESTIC/OVERSEAS), 입력 순서 유지. */
    private List<String> normalizeTypes(OpenAccountRequest request) {
        if (request == null || request.accountTypes() == null || request.accountTypes().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "accountTypes는 최소 1개 이상이어야 합니다.");
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String raw : request.accountTypes()) {
            if (raw == null || !MARKET_SUFFIX.containsKey(raw)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "지원하지 않는 계좌 유형입니다: " + raw);
            }
            seen.putIfAbsent(raw, true);
        }
        return new ArrayList<>(new LinkedHashSet<>(seen.keySet()));
    }

    /** 5자리 계좌 베이스 번호 생성 (예: 98765) */
    private String generateBase() {
        return String.format("%05d", ThreadLocalRandom.current().nextInt(10000, 100000));
    }

    /** 기존 계좌의 암호문에서 베이스('-' 앞부분) 추출 */
    private String baseOf(SecuritiesAccount account) {
        String accountNo = cipher.decrypt(account.getAccountNoEnc());
        int dash = accountNo.indexOf('-');
        return dash > 0 ? accountNo.substring(0, dash) : accountNo;
    }
}
