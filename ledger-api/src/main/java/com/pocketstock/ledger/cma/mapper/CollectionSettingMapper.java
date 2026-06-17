package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CollectionSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionSettingMapper {

    List<CollectionSetting> findByUserId(@Param("userId") Long userId);

    int upsert(CollectionSetting setting);
}
