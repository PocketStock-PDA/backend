package com.pocketstock.core.asset.domain;

import lombok.Getter;
import lombok.Setter;

/** linked_institutions INSERT용(생성 id 회수). 연동 시 user↔기관 커넥션 노드. */
@Getter
@Setter
public class LinkedInstitution {
    private Long id;
    private Long userId;
    private Long institutionMasterId;
    private String linkStatus;
}
