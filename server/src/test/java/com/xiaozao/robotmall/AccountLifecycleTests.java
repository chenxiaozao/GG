package com.xiaozao.robotmall;

import com.xiaozao.robotmall.service.AuthService;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.repository.UserRepository;
import com.xiaozao.robotmall.service.RecommendationRules;
import com.xiaozao.robotmall.entity.Product;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountLifecycleTests {
    @Test void logoutAndPasswordChangeRevokeToken() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        var auth = new AuthService(users, encoder);
        User user = new User(); user.token = "old"; user.password = encoder.encode("oldpassword");
        when(users.findByToken("old")).thenReturn(Optional.of(user));
        auth.logout("old");
        assertNull(user.token); assertNull(user.tokenExpire);
        user.token = "next";
        assertThrows(BizException.class, () -> auth.changePassword(user, "wrong", "newpassword"));
        assertEquals("next", user.token);
        auth.changePassword(user, "oldpassword", "newpassword");
        assertNull(user.token); assertTrue(encoder.matches("newpassword", user.password));
    }
    @Test void strictBudgetStockAndExplicitRelaxation() {
        Product p = new Product(); p.stock = 1; p.price = new BigDecimal("1100");
        assertFalse(RecommendationRules.eligible(p, new BigDecimal("1000"), false));
        assertTrue(RecommendationRules.eligible(p, new BigDecimal("1000"), true));
        p.stock = 0;
        assertFalse(RecommendationRules.eligible(p, null, true));
        p.stock = 1; p.enabled = false;
        assertFalse(RecommendationRules.eligible(p, null, true));
    }
}
