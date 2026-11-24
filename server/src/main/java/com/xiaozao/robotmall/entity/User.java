package com.xiaozao.robotmall.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** 表示账号资料、密码散列和会话。 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String username;

    @JsonIgnore public String password;
    public String nickname;
    public String phone;

    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean phoneVerified = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean mustChangePassword = false;

    public String email;

    @Column(length = 500)
    public String avatar;

    public String role = "USER";
    public boolean enabled = true;
    public String theme = "classic";
    public String accentColor = "#d9363e";
    public String backgroundColor = "#f5f6f8";
    public String interfaceLayout = "standard";
    @JsonIgnore public String token;
    public LocalDateTime tokenExpire;
    public LocalDateTime createTime;

    /** 构造供持久化框架初始化的记录。 */
    public User() {}
}
