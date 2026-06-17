package com.pocketstock.user.member.mapper;

import com.pocketstock.user.member.domain.TermsAgreement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TermsMapper {

    /** 약관 동의 이력 다건 INSERT. */
    int insertAgreements(@Param("list") List<TermsAgreement> list);
}
