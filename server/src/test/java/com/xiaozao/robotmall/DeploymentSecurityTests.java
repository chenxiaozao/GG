package com.xiaozao.robotmall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.config.ApiAccessGuard;
import com.xiaozao.robotmall.entity.User;
import com.xiaozao.robotmall.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentSecurityTests {
    private ApiAccessGuard guard(String role) {
        AuthService auth = mock(AuthService.class);
        User user = new User(); user.id = 1L; user.role = role;
        when(auth.current("valid")).thenReturn(user);
        when(auth.current("")).thenThrow(new BizException("登录已过期"));
        ApiAccessGuard guard = new ApiAccessGuard(auth, new ObjectMapper());
        ReflectionTestUtils.setField(guard, "authMinute", 2);
        return guard;
    }
    private int call(ApiAccessGuard guard, String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (token != null) request.addHeader("X-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        guard.preHandle(request, response, new Object());
        return response.getStatus();
    }
    @Test void anonymousCannotReadAccountsOrUseAi() throws Exception {
        var g = guard("USER");
        assertEquals(200, call(g, "GET", "/api/products", null));
        assertEquals(200, call(g, "GET", "/api/health", null));
        assertEquals(401, call(g, "POST", "/api/health", null));
        for (String path : new String[]{"/api/profile/summary", "/api/orders", "/api/admin/users", "/api/new-private-endpoint"})
            assertEquals(401, call(g, "GET", path, null));
        assertEquals(401, call(g, "POST", "/api/advisor/chat", null));
    }
    @Test void roleAndAuthRateAreEnforced() throws Exception {
        var g = guard("USER");
        assertEquals(403, call(g, "GET", "/api/admin/users", "valid"));
        assertEquals(200, call(guard("ADMIN"), "GET", "/api/admin/users", "valid"));
        assertEquals(200, call(g, "POST", "/api/advisor/chat", "valid"));
        assertEquals(200, call(g, "POST", "/api/advisor/chat", "valid"));
        assertEquals(200, call(g, "POST", "/api/advisor/chat", "valid")); // quota is charged in controller after scope routing
        assertEquals(200, call(g, "POST", "/api/auth/login", null));
        assertEquals(200, call(g, "POST", "/api/auth/register", null));
        assertEquals(429, call(g, "POST", "/api/auth/login", null));
    }
    @Test void userSerializationDoesNotLeakCredentials() throws Exception {
        User user = new User(); user.password = "secret"; user.token = "private-token";
        String json = new ObjectMapper().writeValueAsString(user);
        assertFalse(json.contains("secret")); assertFalse(json.contains("private-token"));
    }
}
