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

    /** 특정 종목 DRIP ON 여부 — 행 없으면 null(=OFF). 배당 지급 엔진의 재투자 분기용. */
    Boolean isEnabled(@Param("userId") Long userId, @Param("stockCode") String stockCode);
}
