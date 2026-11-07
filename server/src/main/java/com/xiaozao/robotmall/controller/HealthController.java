package com.xiaozao.robotmall.controller;

import com.xiaozao.robotmall.common.ApiResponse;
import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public readiness only: never expose credentials, database addresses or stack traces. */
@RestController
public class HealthController {
    private final JdbcTemplate database;
    private final ApplicationAvailability availability;

    public HealthController(JdbcTemplate database, ApplicationAvailability availability) {
        this.database = database;
        this.availability = availability;
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            try {
                if (Integer.valueOf(1).equals(database.queryForObject("SELECT 1", Integer.class))) {
                    return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
                }
            } catch (RuntimeException ignored) {
                // The public response deliberately omits the connection failure details.
            }
        }
        return ResponseEntity.status(503).body(new ApiResponse<>(503, "服务尚未就绪", Map.of("status", "DOWN")));
    }
}
