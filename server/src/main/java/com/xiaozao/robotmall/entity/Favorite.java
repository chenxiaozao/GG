package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames= {
    "userId","productId"
}
)) public class Favorite {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public Long productId;
    public Favorite() {
    }
}