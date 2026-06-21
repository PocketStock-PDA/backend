package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaAutoChargeSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmaAutoChargeSettingMapper {

    /** 사용자당 0 또는 1행. 없으면 null. */
    CmaAutoChargeSetting findByUserId(@Param("userId") Long userId);

    /** user_id UNIQUE 기준 upsert. */
    int upsert(CmaAutoChargeSetting setting);
}
