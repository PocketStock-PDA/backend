package com.pocketstock.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * core-api 실행 앱 - DB A(pocketstock_main).
 * user 모듈 전체(member 회원 + security 인증) + asset/budget/portfolio/notification 도메인을 스캔.
 */
@SpringBootApplication(scanBasePackages = {
        "com.pocketstock.core",
        "com.pocketstock.user",
        "com.pocketstock.common"
})
@MapperScan({"com.pocketstock.core.**.mapper", "com.pocketstock.user.member.mapper"})
public class CoreApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
