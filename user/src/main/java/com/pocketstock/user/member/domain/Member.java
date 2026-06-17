package com.pocketstock.user.member.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 회원(users, DB A) 도메인.
 * INSERT 시 MyBatis가 생성된 PK를 setId로 채워넣으므로 가변(@Setter) 이어야 한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    private Long id;
    private String username;
    private String passwordHash;
    private String name;
    private String phone;
    private LocalDate birthDate;
    private String gender;      // MALE / FEMALE
}
