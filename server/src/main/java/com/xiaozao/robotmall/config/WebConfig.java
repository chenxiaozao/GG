package com.xiaozao.robotmall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAccessGuard guard;
    @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String origins;
    @org.springframework.beans.factory.annotation.Value("${app.avatar-dir:./data/avatars}")
    private String avatarDirectory;

    public WebConfig(ApiAccessGuard guard) { this.guard = guard; }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Token")
                .allowCredentials(false);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(java.nio.file.Paths.get(avatarDirectory).toAbsolutePath().normalize().toUri().toString().replaceAll("/?$", "/"));
    }
}
