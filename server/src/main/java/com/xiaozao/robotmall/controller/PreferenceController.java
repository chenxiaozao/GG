package com.xiaozao.robotmall.controller;

import org.springframework.web.bind.annotation.*;
import com.xiaozao.robotmall.common.ApiResponse;
import com.xiaozao.robotmall.service.*;
import java.util.Map;

/** 提供购物画像的查询、授权、纠正与清除接口。 */
@RestController
@RequestMapping("/api/profile/preferences")
public class PreferenceController {
    private final AuthService auth;
    private final ShoppingPreferenceService preferences;

    /** 初始化当前对象及其依赖。 */
    public PreferenceController(AuthService auth, ShoppingPreferenceService preferences) {
        this.auth = auth;
        this.preferences = preferences;
    }

    /** 读取本人画像。 */
    @GetMapping
    public ApiResponse<?> get(@RequestHeader("X-Token") String token) {
        return ApiResponse.ok(preferences.view(auth.current(token).id, false));
    }

    /** 保存画像采集和管理员共享授权。 */
    @PutMapping("/consent")
    public ApiResponse<?> consent(
            @RequestHeader("X-Token") String token, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(
                preferences.consent(
                        auth.current(token).id,
                        Boolean.TRUE.equals(body.get("enabled")),
                        Boolean.TRUE.equals(body.get("adminSharing"))));
    }

    /** 确认或纠正本人画像内容。 */
    @PutMapping
    public ApiResponse<?> confirm(
            @RequestHeader("X-Token") String token, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(preferences.confirm(auth.current(token).id, body));
    }

    /** 清除本人画像数据。 */
    @DeleteMapping
    public ApiResponse<?> clear(@RequestHeader("X-Token") String token) {
        preferences.clear(auth.current(token).id);
        return ApiResponse.ok();
    }
}
