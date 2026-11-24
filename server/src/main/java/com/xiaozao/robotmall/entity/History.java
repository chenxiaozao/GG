package com.xiaozao.robotmall.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** 表示账号浏览记录。 */
@Entity
@Table(name = "browsing_history")
public class History {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long productId;
    public LocalDateTime viewedAt;

    /** 构造供持久化框架初始化的记录。 */
    public History() {}
}
