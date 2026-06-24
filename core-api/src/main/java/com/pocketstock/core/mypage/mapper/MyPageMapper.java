package com.pocketstock.core.mypage.mapper;

import com.pocketstock.core.mypage.dto.LinkedInstitutionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MyPageMapper {

    /** 연동된 기관 목록(link_status=LINKED). sort_order 순. */
    List<LinkedInstitutionRow> findLinkedInstitutions(@Param("userId") Long userId);
}
