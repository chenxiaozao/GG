package com.xiaozao.robotmall.controller;
import org.springframework.web.bind.annotation.*;
import com.xiaozao.robotmall.common.ApiResponse;
import com.xiaozao.robotmall.service.*;
import java.util.Map;

@RestController @RequestMapping("/api/profile/preferences")
public class PreferenceController {
    private final AuthService auth;private final ShoppingPreferenceService preferences;
    public PreferenceController(AuthService auth,ShoppingPreferenceService preferences){this.auth=auth;this.preferences=preferences;}
    @GetMapping public ApiResponse<?> get(@RequestHeader("X-Token") String token){return ApiResponse.ok(preferences.view(auth.current(token).id,false));}
    @PutMapping("/consent") public ApiResponse<?> consent(@RequestHeader("X-Token") String token,@RequestBody Map<String,Object> body){return ApiResponse.ok(preferences.consent(auth.current(token).id,Boolean.TRUE.equals(body.get("enabled")),Boolean.TRUE.equals(body.get("adminSharing"))));}
    @PutMapping public ApiResponse<?> confirm(@RequestHeader("X-Token") String token,@RequestBody Map<String,Object> body){return ApiResponse.ok(preferences.confirm(auth.current(token).id,body));}
    @DeleteMapping public ApiResponse<?> clear(@RequestHeader("X-Token") String token){preferences.clear(auth.current(token).id);return ApiResponse.ok();}
}
