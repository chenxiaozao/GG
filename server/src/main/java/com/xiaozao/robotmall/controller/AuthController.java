package com.xiaozao.robotmall.controller;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.xiaozao.robotmall.common.ApiResponse;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.service.AuthService;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    private User me(String token) { return auth.current(token); }
    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody Map<String, String> b) {
        User user = auth.login(b.get("username"), b.get("password"));
        return ApiResponse.ok(Map.of("user", user, "token", user.token));
    }

    @PostMapping("/auth/register")
    public ApiResponse<?> register(@RequestBody Map<String, String> b) {
        return ApiResponse.ok(auth.register(
                b.get("username"),
                b.get("password"),
                b.getOrDefault("nickname", b.get("username"))
        ));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<?> logout(@RequestHeader("X-Token") String token) {
        auth.logout(token);
        return ApiResponse.ok();
    }

    @GetMapping("/auth/me")
    public ApiResponse<?> current(@RequestHeader("X-Token") String t) {
        return ApiResponse.ok(me(t));
    }

}
