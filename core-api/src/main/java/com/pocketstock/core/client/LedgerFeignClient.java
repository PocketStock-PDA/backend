package com.pocketstock.core.client;

import com.pocketstock.core.client.dto.CollectionSettingView;
import com.pocketstock.core.client.dto.UsdKrwRateView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * core→ledger 내부 호출(읽기 전용). 잔돈 스캔에서만 사용:
 * - collection_settings(DB B): 끝전 임계값·활성 소스 — core는 자체 DB A 잔액에 이 설정만 입혀 로컬 계산.
 * - 환율(USD/KRW): 외화 잔돈 KRW 환산용 매매기준율.
 *
 * <p>ledger 전체 수집 계산(getHome 등)을 호출하지 않는다 — getHome이 다시 core를 Feign으로 부르므로
 * core→ledger→core 순환이 된다(F-E=B). 계산은 core가 보유한 원천 데이터로 직접 한다.
 */
@FeignClient(name = "ledger-api", url = "${feign.ledger-api.url}")
public interface LedgerFeignClient {

    @GetMapping("/internal/cma/collection-settings")
    List<CollectionSettingView> getCollectionSettings(@RequestParam("userId") Long userId);

    @GetMapping("/internal/exchange/usd-krw-rate")
    UsdKrwRateView getUsdKrwRate();
}
