package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity public class Brand {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String name;
    @Column(length=1000) public String description;
    public boolean enabled=true;
    public Brand() {
    }
}