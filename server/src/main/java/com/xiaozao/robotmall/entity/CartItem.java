package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示购物车明细。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}))
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long productId;
    public int quantity;
    public String selectedSku;

    /** 构造供持久化框架初始化的记录。 */
    public CartItem() {}
}
