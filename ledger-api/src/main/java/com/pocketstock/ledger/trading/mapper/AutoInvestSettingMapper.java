package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.AutoInvestSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AutoInvestSettingMapper {

    /** 유저 전역 설정 1행 조회(없으면 null). */
    AutoInvestSetting findByUserId(@Param("userId") Long userId);

    /** 최초 진입 시 기본 설정 1행 생성. */
    int insert(AutoInvestSetting setting);

    /** 전역 스위치(enabled/paused/keepCollecting) 갱신 — user_id 가드. */
    int update(AutoInvestSetting setting);
}
