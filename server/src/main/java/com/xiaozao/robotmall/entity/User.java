package com.xiaozao.robotmall.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public String username;
    @JsonIgnore
    public String password;
    public String nickname;
    public String phone;
    public String email;
    @Column(length = 500)
    public String avatar;
    public String role = "USER";
    public boolean enabled = true;
    public String theme = "classic";
    public String accentColor = "#d9363e";
    public String backgroundColor = "#f5f6f8";
    public String interfaceLayout = "standard";
    @JsonIgnore
    public String token;
    public LocalDateTime tokenExpire;
    public LocalDateTime createTime;

    public User() {
    }
}
