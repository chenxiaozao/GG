package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

/** 表示订单商品快照。 */
@Entity
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long orderId;
    public Long productId;
    public String productName;
    public BigDecimal price;
    public BigDecimal discountAmount;
    public int quantity;
    public String selectedSku;

    /** 构造供持久化框架初始化的记录。 */
    public OrderItem() {}
}
