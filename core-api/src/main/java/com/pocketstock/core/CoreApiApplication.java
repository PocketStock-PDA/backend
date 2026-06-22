package com.pocketstock.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

/**
 * core-api 실행 앱 - DB A(pocketstock_main).
 * user 모듈 전체(member 회원 + security 인증) + asset/budget/portfolio/notification 도메인을 스캔.
 * core→ledger Feign(잔돈 스캔 시 ledger collection_settings·환율 read)은 @EnableFeignClients로 활성화.
 */
@SpringBootApplication(scanBasePackages = {
        "com.pocketstock.core",
        "com.pocketstock.user",
        "com.pocketstock.common"
})
@EnableFeignClients(basePackages = "com.pocketstock.core.client")
@MapperScan({"com.pocketstock.core.**.mapper", "com.pocketstock.user.member.mapper"})
public class CoreApiApplication {
    public static void main(String[] args) {
        // 서버 타임존을 UTC로 고정 — 로컬(KST)·EC2(UTC) 환경차로 시간이 흔들리는 것 방지.
        // 한국·미국 시간은 화면 표시 등 필요 시 ZoneId로 변환해 쓴다. (ledger-api와 동일)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
