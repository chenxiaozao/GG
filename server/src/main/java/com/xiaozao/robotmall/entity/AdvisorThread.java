package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 保存一条独立咨询会话和它自己的商品候选。 */
@Entity
@Table(indexes = @Index(name = "advisor_thread_owner_time", columnList = "user_id, updated_at"))
public class AdvisorThread {
    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 120)
    public String title = "新对话";

    @Column(nullable = false, length = 100)
    public String candidateIds = "";

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    public String messagesJson = "[]";

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
