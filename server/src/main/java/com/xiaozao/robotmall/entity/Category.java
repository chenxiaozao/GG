package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示商品分类。 */
@Entity
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public Long parentId = 0L;
    public int sortNo;
    public boolean enabled = true;

    /** 构造供持久化框架初始化的记录。 */
    public Category() {}
}
