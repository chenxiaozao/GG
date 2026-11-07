package com.xiaozao.robotmall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.ApiResponse;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Default-deny API gate; ownership checks remain in the business controllers. */
@Component
public class ApiAccessGuard implements HandlerInterceptor {
    private final AuthService auth;
    private final ObjectMapper mapper;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private record Bucket(long expires, int count) {}
    @Value("${app.auth-minute-limit:20}") private int authMinute;

    public ApiAccessGuard(AuthService auth, ObjectMapper mapper) {
        this.auth = auth;
        this.mapper = mapper;
    }

    // Bounded single-process counters. Restart resets quotas; use shared storage for multiple replicas.
    synchronized boolean allow(String key, int limit, long window) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> e.getValue().expires <= now);
        Bucket old = buckets.get(key);
        if (limit <= 0 || (old == null && buckets.size() >= 10000)) return false;
        if (old != null && old.count >= limit) return false;
        buckets.put(key, new Bucket(old == null ? now + window : old.expires, old == null ? 1 : old.count + 1));
        return true;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) return true;
        if ("POST".equals(method) && Set.of("/api/auth/login", "/api/auth/register").contains(path)) {
            // Do not trust arbitrary client-supplied X-Forwarded-For.
            return allow("auth:" + request.getRemoteAddr(), authMinute, 60000) || reject(response, 429, "操作过于频繁，请稍后重试");
        }
        if ("GET".equals(method) && (Set.of("/api/health", "/api/categories", "/api/brands", "/api/products", "/api/advisor/ai-status", "/api/compatibility/check").contains(path)
                || path.matches("/api/products/\\d+(?:/reviews|/accessories)?"))) return true;
        if ("POST".equals(method) && Set.of("/api/products/compare", "/api/compatibility/analyze", "/api/advisor", "/api/advisor/recommend", "/api/trade-in/estimate").contains(path)) return true;
        User user;
        try { user = auth.current(Objects.toString(request.getHeader("X-Token"), "")); }
        catch (com.xiaozao.robotmall.common.BizException error) { return reject(response, 401, "登录已过期，请重新登录"); }
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(user.role)) return reject(response, 403, "无管理员权限");
        if (user.mustChangePassword && !Set.of("/api/auth/me","/api/auth/logout","/api/profile/password").contains(path)) return reject(response,403,"请先修改管理员设置的临时密码");
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 429) response.setHeader("Retry-After", "60");
        mapper.writeValue(response.getWriter(), new ApiResponse<>(status, message, null));
        return false;
    }
}
