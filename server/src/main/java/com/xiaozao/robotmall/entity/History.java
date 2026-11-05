package com.xiaozao.robotmall.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity @Table(name="browsing_history") public class History {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public Long productId;
    public LocalDateTime viewedAt;
    public History() {
    }
}