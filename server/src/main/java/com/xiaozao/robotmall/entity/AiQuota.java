package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity
public class AiQuota {
    @Id @Column(length=80) public String id;
    public long dayWindow;
    public int dayCount;
    public long minuteWindow;
    public int minuteCount;
    public AiQuota() {}
    public AiQuota(String id) { this.id = id; }
}
