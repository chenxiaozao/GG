package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

/** 表示主机与配件适配关系。 */
@Entity
@Table(
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"robotProductId", "accessoryProductId"}))
public class Compatibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long robotProductId;
    public Long accessoryProductId;
    public String note;
    public String compatibilityLevel = "COMPATIBLE";
    public String interfaceType;
    public String supportedModels;
    public String generationRequirement;
    public String sizeRequirement;
    public String firmwareRequirement;
    public String riskNote;

    /** 构造供持久化框架初始化的记录。 */
    public Compatibility() {}
}
