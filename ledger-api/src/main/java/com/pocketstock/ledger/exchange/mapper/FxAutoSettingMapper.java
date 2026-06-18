package com.pocketstock.ledger.exchange.mapper;

import com.pocketstock.ledger.exchange.domain.FxAutoSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FxAutoSettingMapper {

    FxAutoSetting findByUserId(@Param("userId") Long userId);

    /** 1인 1행 — user_id UNIQUE 기준 upsert. */
    int upsert(FxAutoSetting setting);
}
