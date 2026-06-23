package com.pocketstock.ledger.dev;

import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.support.CmaAccountNoCipher;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.support.AccountNoCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 로컬 시드 계좌번호 적재(@Profile local 전용) — 시드 SQL은 {@code account_no_enc}를 NULL로 두는데
 * (SQL로 AES-256-GCM 암호문을 만들 수 없어서), 부팅 시 현재 키로 암호화해 채운다.
 * CMA·국내위탁·해외위탁 3계좌 모두 채워, 계좌번호 조회/마스킹이 정상 동작한다.
 *
 * <p><b>키 회전 안전</b>: 매 부팅 <b>덮어쓰기</b>라 키({@code account.cipher.secret})가 바뀌어도
 * 다음 부팅에서 현재 키로 재암호화돼 복호화가 깨지지 않는다(NULL일 때만 채우는 방식은 옛 키 암호문이
 * 남아 깨진다 — 그래서 항상 덮어쓴다). 운영 프로파일에선 동작하지 않는다(실계좌는 개설 시 암호화).
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedAccountNoInitializer {

    private static final Long SEED_USER_ID = 1L;

    private final SecuritiesAccountMapper securitiesMapper;
    private final CmaAccountMapper cmaMapper;
    private final AccountNoCipher securitiesCipher;
    private final CmaAccountNoCipher cmaCipher;

    @EventListener(ApplicationReadyEvent.class)
    public void fillSeedAccountNumbers() {
        int n = 0;
        n += fillSecurities("DOMESTIC", "27-1234567-01");   // 국내 위탁
        n += fillSecurities("OVERSEAS", "27-1234567-02");   // 해외 위탁
        n += fillCma("110-456-789012");                     // CMA(신한투자증권)
        if (n > 0) {
            log.info("[local seed] 계좌번호 {}건 현재 키로 암호화 적재(account_no_enc)", n);
        }
    }

    private int fillSecurities(String market, String accountNo) {
        SecuritiesAccount acc = securitiesMapper.findByUserIdAndMarket(SEED_USER_ID, market);
        if (acc == null) {
            return 0;   // 시드 없는 환경 — 무해
        }
        return securitiesMapper.updateAccountNoEnc(acc.getId(), securitiesCipher.encrypt(accountNo));
    }

    private int fillCma(String accountNo) {
        CmaAccount acc = cmaMapper.findByUserId(SEED_USER_ID);
        if (acc == null) {
            return 0;
        }
        return cmaMapper.updateAccountNoEnc(acc.getId(), cmaCipher.encrypt(accountNo));
    }
}
