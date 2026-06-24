package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CollectionSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionSettingMapper {

    List<CollectionSetting> findByUserId(@Param("userId") Long userId);

    int upsert(CollectionSetting setting);

    /** 특정 소스타입(예: CARD) 전체 행의 활성 상태를 일괄 변경. 마이페이지 마스터 토글용. 변경된 행 수 반환. */
    int updateEnabledBySourceType(
            @Param("userId") Long userId,
            @Param("sourceType") String sourceType,
            @Param("enabled") boolean enabled
    );
}
