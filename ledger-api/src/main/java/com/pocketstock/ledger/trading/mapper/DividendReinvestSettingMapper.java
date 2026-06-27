package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.DividendReinvestSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DividendReinvestSettingMapper {

    /** 토글 upsert — (user_id, stock_code) UNIQUE라 있으면 is_enabled만 갱신. */
    int upsert(DividendReinvestSetting setting);

    /** 유저의 DRIP 설정 전체(종목명 join, 최신순). */
    List<DividendReinvestSetting> findByUserId(@Param("userId") Long userId);

    /**
     * 특정 종목 DRIP 상태 — 행 없으면 null, OFF면 FALSE, ON이면 TRUE. 배당 지급 엔진의 재투자 분기용.
     * 호출부는 {@code Boolean.TRUE.equals(...)}로 판정하므로 null(미설정)·FALSE(OFF) 모두 "재투자 안 함"이다.
     */
    Boolean isEnabled(@Param("userId") Long userId, @Param("stockCode") String stockCode);
}
