package com.pocketstock.user.member.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPassword {
    Long id;
    Long userId;
    String passwordHash;
    Boolean isLocked;
}
