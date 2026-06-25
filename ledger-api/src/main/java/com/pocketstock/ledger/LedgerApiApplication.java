package com.pocketstock.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

/**
 * ledger-api 실행 앱 - DB B(pocketstock_ledger, 원장).
 * cma/exchange/trading 도메인 + user.security(JWT)만 스캔.
 * 회원 도메인(user.member, DB A)은 스캔하지 않아 DB A 결합이 없다.
 */
@EnableFeignClients(basePackages = "com.pocketstock.ledger.client")
@SpringBootApplication(scanBasePackages = {
        "com.pocketstock.ledger",
        "com.pocketstock.common",
        "com.pocketstock.user.security"
})
// recon·outbox는 단일 횡단 매퍼라 .mapper 하위가 아닌 패키지 직속 → 글롭에 추가로 포함(#96·#204 클린기동)
@MapperScan({"com.pocketstock.ledger.**.mapper", "com.pocketstock.ledger.recon", "com.pocketstock.ledger.outbox"})
public class LedgerApiApplication {
    public static void main(String[] args) {
        // 서버 타임존을 UTC로 고정 — 로컬(KST)·EC2(UTC) 환경차로 시간이 흔들리는 것 방지.
        // 한국·미국 시간은 화면 표시나 세션 판정 시 ZoneId로 변환해 쓴다.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(LedgerApiApplication.class, args);
    }
}
