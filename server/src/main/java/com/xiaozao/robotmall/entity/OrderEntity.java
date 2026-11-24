package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** 表示订单主记录及交易状态。 */
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true)
    public String orderNo;

    public Long userId;
    public BigDecimal totalAmount;
    public BigDecimal discountAmount = BigDecimal.ZERO;
    public Long tradeInCreditId;
    public String status;
    public String receiverInfo;
    public String logisticsCompany;
    public String logisticsNo;
    public LocalDateTime createdAt;
    public LocalDateTime paidAt;

    /** 构造供持久化框架初始化的记录。 */
    public OrderEntity() {}
}
