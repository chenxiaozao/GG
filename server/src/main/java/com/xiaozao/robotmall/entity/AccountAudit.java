package com.xiaozao.robotmall.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class AccountAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long actorId;
    public Long targetId;
    public String action;
    @Column(length=200) public String reason;
    public LocalDateTime createdAt = LocalDateTime.now();
}
