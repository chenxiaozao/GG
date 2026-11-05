package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
public class AfterSaleRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true) public String serviceNo;
    public Long userId;
    public Long orderId;
    public Long orderItemId;
    public Long productId;
    public String productName;
    public String type;
    public String reason;
    @Column(length = 1000) public String description;
    public String status;
    public BigDecimal requestedAmount;
    public BigDecimal approvedAmount;
    public String returnLogisticsCompany;
    public String returnLogisticsNo;
    @Column(length = 1000) public String adminReply;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime completedAt;
    @Transient public java.util.List<String> allowedStatuses;
}
