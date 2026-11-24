package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示商品品牌。 */
@Entity
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(length = 1000)
    public String description;

    public boolean enabled = true;

    /** 构造供持久化框架初始化的记录。 */
    public Brand() {}
}
