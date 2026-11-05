package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames= {
    "userId","productId"
}
)) public class CartItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public Long productId;
    public int quantity;
    public String selectedSku;
    public CartItem() {
    }
}