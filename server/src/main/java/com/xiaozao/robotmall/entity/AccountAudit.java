package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 表示后台账号操作审计。 */
@Entity
public class AccountAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long actorId;
    public Long targetId;
    public String action;

    @Column(length = 200)
    public String reason;

    public LocalDateTime createdAt = LocalDateTime.now();
}
