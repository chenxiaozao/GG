package com.xiaozao.robotmall.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.xiaozao.robotmall.common.ApiResponse;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.service.AuthService;

/** 提供注册、登录、退出和查询当前账号的入口。 */
@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    /** 初始化当前对象及其依赖。 */
    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** 校验会话并返回对应账号。 */
    private User me(String token) {
        return auth.current(token);
    }

    /** 验证密码并返回用户和会话令牌。 */
    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody Map<String, String> b) {
        User user = auth.login(b.get("username"), b.get("password"));
        return ApiResponse.ok(Map.of("user", user, "token", user.token));
    }

    /** 校验注册信息并建立新账号。 */
    @PostMapping("/auth/register")
    public ApiResponse<?> register(@RequestBody Map<String, String> b) {
        return ApiResponse.ok(
                auth.register(
                        b.get("username"),
                        b.get("password"),
                        b.getOrDefault("nickname", b.get("username"))));
    }

    /** 撤销当前会话。 */
    @PostMapping("/auth/logout")
    public ApiResponse<?> logout(@RequestHeader("X-Token") String token) {
        auth.logout(token);
        return ApiResponse.ok();
    }

    /** 返回已登录账号的公开信息。 */
    @GetMapping("/auth/me")
    public ApiResponse<?> current(@RequestHeader("X-Token") String t) {
        return ApiResponse.ok(me(t));
    }
}
