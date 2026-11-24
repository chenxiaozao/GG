package com.xiaozao.robotmall.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.xiaozao.robotmall.common.*;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import com.xiaozao.robotmall.service.*;
import java.util.*;

/** 提供管理员维护账号与查看操作记录的接口。 */
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AuthService auth;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ShoppingPreferenceService preferences;
    private final AccountAuditRepository audits;

    /** 初始化当前对象及其依赖。 */
    public AdminAccountController(
            AuthService auth,
            UserRepository users,
            PasswordEncoder encoder,
            ShoppingPreferenceService preferences,
            AccountAuditRepository audits) {
        this.auth = auth;
        this.users = users;
        this.encoder = encoder;
        this.preferences = preferences;
        this.audits = audits;
    }

    /** 读取当前管理员并检查操作权限。 */
    private User admin(String token) {
        User user = auth.current(token);
        if (!"ADMIN".equals(user.role)) throw new BizException("无管理员权限");
        return user;
    }

    /** 锁定待维护账号并拒绝维护管理员。 */
    private User target(long id) {
        User target = users.findForUpdate(id).orElseThrow(() -> new BizException("账号不存在"));
        if ("ADMIN".equals(target.role)) throw new BizException("不能维护管理员账号，请使用本人安全设置");
        return target;
    }

    /** 重新校验操作者密码和维护原因。 */
    private void verify(User actor, Map<String, String> body) {
        if (!encoder.matches(Objects.toString(body.get("adminPassword"), ""), actor.password))
            throw new BizException("管理员密码验证失败");
        if (!Set.of("用户申请", "账号找回", "资料纠错", "安全处理")
                .contains(Objects.toString(body.get("reason"), "")))
            throw new BizException("请选择操作原因");
    }

    /** 记录操作者、目标账号和维护动作。 */
    private void audit(User actor, long target, String action, String reason) {
        AccountAudit record = new AccountAudit();
        record.actorId = actor.id;
        record.targetId = target;
        record.action = action;
        record.reason = reason;
        audits.save(record);
    }

    /** 设置临时密码并撤销原会话。 */
    @PostMapping("/{id}/reset-password")
    @Transactional
    public ApiResponse<?> reset(
            @RequestHeader("X-Token") String token,
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        User actor = admin(token);
        verify(actor, body);
        User target = target(id);
        String password = Objects.toString(body.get("newPassword"), "");
        if (password.length() < 12
                || password.length() > 64
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BizException("临时密码须为12至64位且UTF-8不超过72字节");
        target.password = encoder.encode(password);
        target.mustChangePassword = true;
        target.token = null;
        target.tokenExpire = null;
        users.save(target);
        audit(actor, id, "RESET_PASSWORD", body.get("reason"));
        return ApiResponse.ok(Map.of("message", "临时密码已设置，旧登录已撤销；用户需首次登录改密"));
    }

    /** 维护手机号并重置验证状态。 */
    @PutMapping("/{id}/phone")
    @Transactional
    public ApiResponse<?> phone(
            @RequestHeader("X-Token") String token,
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        User actor = admin(token);
        verify(actor, body);
        User target = target(id);
        String phone = Objects.toString(body.get("phone"), "").trim();
        if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) throw new BizException("手机号格式无效");
        target.phone = phone;
        target.phoneVerified = false;
        users.save(target);
        audit(actor, id, "UPDATE_UNVERIFIED_PHONE", body.get("reason"));
        return ApiResponse.ok(Map.of("message", "联系方式已更新，未短信验证，不是登录绑定"));
    }

    /** 按授权读取购物画像并记录访问。 */
    @GetMapping("/{id}/preferences")
    @Transactional
    public ApiResponse<?> preferences(
            @RequestHeader("X-Token") String token, @PathVariable long id) {
        User actor = admin(token);
        if (!users.existsById(id)) throw new BizException("账号不存在");
        var result = preferences.view(id, true);
        audit(
                actor,
                id,
                "VIEW_PREFERENCE",
                Boolean.TRUE.equals(result.get("visible")) ? "用户授权可见" : "用户未授权，未展示内容");
        return ApiResponse.ok(result);
    }

    /** 读取该账号最近的维护审计记录。 */
    @GetMapping("/{id}/audit")
    public ApiResponse<?> audit(@RequestHeader("X-Token") String token, @PathVariable long id) {
        admin(token);
        return ApiResponse.ok(audits.findTop50ByTargetIdOrderByCreatedAtDesc(id));
    }
}
