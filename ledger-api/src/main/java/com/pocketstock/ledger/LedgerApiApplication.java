package com.pocketstock.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

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
@MapperScan("com.pocketstock.ledger.**.mapper")
public class LedgerApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApiApplication.class, args);
    }
}
