package com.xiaozao.robotmall;

import com.xiaozao.robotmall.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证应用及数据库就绪条件。 */
class HealthControllerTests {
    /** 验证数据库可访问时才算就绪。 */
    @Test
    void reportsReadyOnlyWhenDatabaseResponds() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(db.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = new HealthController(db, availability).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().getData().get("status"));
    }

    /** 验证初始化完成前健康检查不能成功。 */
    @Test
    void seedingAndStartupMustFinishBeforeReadiness() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals(503, new HealthController(db, availability).health().getStatusCode().value());
        verifyNoInteractions(db);
    }

    /** 验证连接异常不会泄漏内部细节。 */
    @Test
    void connectionFailuresDoNotLeakInternals() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(db.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("secret connection details"));
        var response = new HealthController(db, availability).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().getData().get("status"));
        assertFalse(response.getBody().getMessage().contains("secret"));
    }
}
