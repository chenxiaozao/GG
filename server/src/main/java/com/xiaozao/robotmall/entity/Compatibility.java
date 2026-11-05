package com.xiaozao.robotmall.entity;

import jakarta.persistence.*;

@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames= {
    "robotProductId","accessoryProductId"
}
)) public class Compatibility {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
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
    public Compatibility() {
    }
}
