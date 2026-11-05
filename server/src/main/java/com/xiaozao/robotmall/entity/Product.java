package com.xiaozao.robotmall.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String name;
    public String subtitle;
    public Long categoryId;
    public Long brandId;
    public String type;
    public BigDecimal price;
    public int stock;
    public int sales;
    public double rating;
    public String image;
    public String intelligenceLevel;
    public Integer enduranceMinutes;
    public Double payloadKg;
    public String skuOptions;
    public BigDecimal marketPrice;
    public String sourceUrl;
    public String warranty;
    public String shippingNote;
    @Column(length=4000) public String description;
    @Column(length=4000) public String parametersJson;
    public boolean enabled=true;
    @Transient public String categoryName;
    @Transient public String brandName;
    @Transient public String imageKind;
    @Transient public String imageNote;
    @Transient public String imageSourceUrl;
    @Transient public String imageCheckedAt;
    @Transient public String sourceRegion;
    public Product() {
    }
}
