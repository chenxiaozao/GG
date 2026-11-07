package com.xiaozao.robotmall;

import com.xiaozao.robotmall.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTests {
    @Test void reportsReadyOnlyWhenDatabaseResponds() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(db.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = new HealthController(db, availability).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().getData().get("status"));
    }

    @Test void seedingAndStartupMustFinishBeforeReadiness() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals(503, new HealthController(db, availability).health().getStatusCode().value());
        verifyNoInteractions(db);
    }

    @Test void connectionFailuresDoNotLeakInternals() {
        var db = mock(JdbcTemplate.class);
        var availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(db.queryForObject("SELECT 1", Integer.class)).thenThrow(new IllegalStateException("secret connection details"));
        var response = new HealthController(db, availability).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().getData().get("status"));
        assertFalse(response.getBody().getMessage().contains("secret"));
    }
}
