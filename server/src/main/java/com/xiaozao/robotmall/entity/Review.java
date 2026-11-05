package com.xiaozao.robotmall.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames= {
    "userId","orderItemId"
}
)) public class Review {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public Long productId;
    public Long orderItemId;
    public int score;
    @Column(length=2000) public String content;
    public LocalDateTime createdAt;
    public boolean visible=true;
    public Review() {
    }
}