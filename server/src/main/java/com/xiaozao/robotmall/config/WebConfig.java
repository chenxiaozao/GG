package com.xiaozao.robotmall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 配置接口拦截、跨域和上传资源映射。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAccessGuard guard;

    @org.springframework.beans.factory.annotation.Value(
            "${app.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String origins;

    @org.springframework.beans.factory.annotation.Value("${app.avatar-dir:./data/avatars}")
    private String avatarDirectory;

    /** 初始化当前对象及其依赖。 */
    public WebConfig(ApiAccessGuard guard) {
        this.guard = guard;
    }

    /** 将访问拦截器绑定到接口路径。 */
    @Override
    public void addInterceptors(
            org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/**");
    }

    /** 按部署配置允许指定网页来源请求接口。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Token")
                .allowCredentials(false);
    }

    /** 把头像网址映射到服务端文件目录。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(
                        java.nio.file.Paths.get(avatarDirectory)
                                .toAbsolutePath()
                                .normalize()
                                .toUri()
                                .toString()
                                .replaceAll("/?$", "/"));
    }
}
