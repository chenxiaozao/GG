package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity public class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String name;
    public Long parentId=0L;
    public int sortNo;
    public boolean enabled=true;
    public Category() {
    }
}