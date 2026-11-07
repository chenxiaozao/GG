package com.xiaozao.robotmall.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public User login(String username, String password) {
        User u = users.findByUsername(username).orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!encoder.matches(password, u.password)) {
            throw new BizException("用户名或密码错误");
        }
        if (!u.enabled) {
            throw new BizException("账号已被禁用");
        }
        u.token = UUID.randomUUID().toString();
        u.tokenExpire = LocalDateTime.now().plusDays(7);
        users.save(u);
        return u;
    }

    public User register(String username, String password, String nickname) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,30}"))
            throw new BizException("用户名须为 3 至 30 位字母、数字或下划线");
        if (password == null || password.length() < 8 || password.length() > 64)
            throw new BizException("密码须为 8 至 64 位");
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BizException("密码的UTF-8长度不能超过72字节，请缩短中文或特殊字符密码");
        if (nickname == null || nickname.isBlank() || nickname.length() > 30)
            throw new BizException("昵称须为 1 至 30 字");
        if (users.findByUsername(username).isPresent()) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.username = username;
        u.password = encoder.encode(password);
        u.nickname = nickname;
        u.role = "USER";
        u.createTime = LocalDateTime.now();
        try { return users.save(u); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicate) { throw new BizException("用户名已存在，请更换用户名"); }
    }

    public User current(String token) {
        User u = users.findByToken(token).orElseThrow(() -> new BizException("登录已过期"));
        if (!u.enabled) {
            throw new BizException("账号已被禁用");
        }
        if (u.tokenExpire == null || u.tokenExpire.isBefore(LocalDateTime.now())) {
            throw new BizException("登录已过期");
        }
        return u;
    }

    public User updateProfile(User u, String nickname, String phone, String email, String avatar) {
        String cleanNickname = nickname == null ? "" : nickname.trim();
        String cleanPhone = phone == null ? "" : phone.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanAvatar = avatar == null ? "" : avatar.trim();
        if (cleanNickname.isBlank() || cleanNickname.length() > 30) throw new BizException("昵称须为 1 至 30 个字符");
        if (!cleanPhone.isBlank() && !cleanPhone.matches("^1[3-9]\\d{9}$")) throw new BizException("请输入正确的手机号码");
        if (!cleanEmail.isBlank() && !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new BizException("请输入正确的邮箱地址");
        if (cleanAvatar.length() > 500) throw new BizException("头像地址过长");
        u.nickname = cleanNickname;
        if (!java.util.Objects.equals(u.phone,cleanPhone)) u.phoneVerified=false;
        u.phone = cleanPhone;
        u.email = cleanEmail;
        u.avatar = cleanAvatar;
        return users.save(u);
    }

    public void changePassword(User u, String oldPassword, String newPassword) {
        if (oldPassword == null || !encoder.matches(oldPassword, u.password)) throw new BizException("原密码不正确");
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 64) throw new BizException("新密码须为 8 至 64 个字符");
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new BizException("密码的UTF-8长度不能超过72字节");
        if (oldPassword.equals(newPassword)) throw new BizException("新密码不能与原密码相同");
        u.password = encoder.encode(newPassword);
        u.mustChangePassword = false;
        u.token = null;
        u.tokenExpire = null;
        users.save(u);
    }

    public void logout(String token) {
        users.findByToken(token).ifPresent(u -> {
            u.token = null;
            u.tokenExpire = null;
            users.save(u);
        });
    }
}
