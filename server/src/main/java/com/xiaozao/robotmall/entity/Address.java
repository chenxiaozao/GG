package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示账号收货地址。 */
@Entity
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public String receiver;
    public String phone;
    public String province;
    public String city;
    public String district;
    public String detail;
    public boolean defaultAddress;

    /** 构造供持久化框架初始化的记录。 */
    public Address() {}
}
