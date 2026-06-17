package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmaAccountMapper {

    CmaAccount findByUserId(@Param("userId") Long userId);

    int insert(CmaAccount account);
}
