package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity public class Address {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String receiver;
    public String phone;
    public String province;
    public String city;
    public String district;
    public String detail;
    public boolean defaultAddress;
    public Address() {
    }
}