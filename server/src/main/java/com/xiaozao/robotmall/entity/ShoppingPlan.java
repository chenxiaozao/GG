package com.xiaozao.robotmall.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class ShoppingPlan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long userId;
 @Column(length=60) public String title;
 @Column(length=800) public String requirement;
 @Column(length=100) public String productIds;
 @Column(length=800) public String note;
 public LocalDateTime updatedAt;
 public java.math.BigDecimal budget;
 @Column(length=30) public String scene;
}
