package com.pocketstock.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업 활성화 — 환율(CUR) 상시구독 하트비트({@code CurrencyRatePinner}) 등.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
