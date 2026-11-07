package com.xiaozao.robotmall.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ShoppingPreference {
    @Id public Long userId;
    public boolean collectionEnabled;
    public boolean adminSharing;
    public boolean confirmed;
    @Column(length=6000) public String preferencesJson = "{}";
    public LocalDateTime updatedAt;
    public LocalDateTime consentAt;
    public ShoppingPreference() {}
}
