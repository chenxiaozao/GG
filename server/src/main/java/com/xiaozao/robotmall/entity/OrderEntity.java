package com.xiaozao.robotmall.entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity @Table(name="orders") public class OrderEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(unique=true) public String orderNo;
    public Long userId;
    public BigDecimal totalAmount;
    public BigDecimal discountAmount=BigDecimal.ZERO;
    public String status;
    public String receiverInfo;
    public String logisticsCompany;
    public String logisticsNo;
    public LocalDateTime createdAt;
    public LocalDateTime paidAt;
    public OrderEntity() {
    }
}