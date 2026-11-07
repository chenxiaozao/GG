package com.xiaozao.robotmall.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class ShoppingPlanVersion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long planId;
 @Column(length=12000) public String snapshotJson;
 public LocalDateTime createdAt;
}
