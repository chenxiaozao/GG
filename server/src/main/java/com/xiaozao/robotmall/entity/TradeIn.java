package com.xiaozao.robotmall.entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity public class TradeIn {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String model;
    public int years;
    public String conditionLevel;
    public boolean functional;
    public BigDecimal estimatedPrice;
    public LocalDateTime createdAt;
    public TradeIn() {
    }
}