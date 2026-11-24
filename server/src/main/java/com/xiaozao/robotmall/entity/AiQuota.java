package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示人工智能调用计数。 */
@Entity
public class AiQuota {
    @Id
    @Column(length = 80)
    public String id;

    public long dayWindow;
    public int dayCount;
    public long minuteWindow;
    public int minuteCount;

    /** 构造供持久化框架初始化的记录。 */
    public AiQuota() {}

    /** 构造供持久化框架初始化的记录。 */
    public AiQuota(String id) {
        this.id = id;
    }
}
