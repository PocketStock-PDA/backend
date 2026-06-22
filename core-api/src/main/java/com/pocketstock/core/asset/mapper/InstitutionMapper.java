package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.InstitutionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InstitutionMapper {

    /** 활성 카탈로그 기관 목록 + 해당 유저 연동 상태(LINKED/AVAILABLE). sort_order 순. */
    List<InstitutionResponse> findInstitutions(@Param("userId") Long userId);
}
