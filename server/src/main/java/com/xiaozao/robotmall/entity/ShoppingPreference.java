package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 表示结构化选购画像；旧授权列仅保留数据库兼容。 */
@Entity
public class ShoppingPreference {
    @Id public Long userId;
    public boolean collectionEnabled;
    public boolean adminSharing;
    public boolean confirmed;

    @Column(length = 6000)
    public String preferencesJson = "{}";

    public LocalDateTime updatedAt;
    public LocalDateTime consentAt;

    /** 构造供持久化框架初始化的记录。 */
    public ShoppingPreference() {}
}
