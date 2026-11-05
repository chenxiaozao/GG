package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long orderId;
    public Long productId;
    public String productName;
    public BigDecimal price;
    public int quantity;
    public String selectedSku;
    public OrderItem() {
    }
}