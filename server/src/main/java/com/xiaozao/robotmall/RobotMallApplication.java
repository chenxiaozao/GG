package com.xiaozao.robotmall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 商城后端应用的启动入口。 */
@SpringBootApplication
public class RobotMallApplication {

    /** 启动 Spring Boot 应用并加载配置与业务组件。 */
    public static void main(String[] args) {
        SpringApplication.run(RobotMallApplication.class, args);
    }
}
