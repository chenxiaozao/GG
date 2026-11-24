package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** 保存旧机模拟估价及尚可用于结算的折价余额。 */
@Entity
public class TradeIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public String brand;
    public String model;
    public int years;
    public String conditionLevel;
    public boolean functional;
    public BigDecimal estimatedPrice;
    public LocalDateTime createdAt;
    public BigDecimal remainingAmount;
    public LocalDateTime expiresAt;

    /** 构造供持久化框架初始化的记录。 */
    public TradeIn() {}
}
