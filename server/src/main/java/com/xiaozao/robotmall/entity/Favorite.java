package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示账号商品收藏及备注。 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}))
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long productId;

    @Column(length = 500)
    public String note;

    /** 构造供持久化框架初始化的记录。 */
    public Favorite() {}
}
