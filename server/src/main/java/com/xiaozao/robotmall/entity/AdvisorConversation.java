package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 保存账号最近的选购对话，与商品浏览记录分开。 */
@Entity
public class AdvisorConversation {
    @Id public Long userId;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    public String messagesJson = "[]";
}
