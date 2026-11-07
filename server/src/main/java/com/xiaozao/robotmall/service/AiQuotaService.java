package com.xiaozao.robotmall.service;

import com.xiaozao.robotmall.entity.AiQuota;
import com.xiaozao.robotmall.repository.AiQuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import java.util.Map;

/** Database-serialized admission, shared by all instances using the same database. */
@Service
public class AiQuotaService implements CommandLineRunner {
    private final AiQuotaRepository repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Value("${app.ai-user-daily-limit:20}") private int userLimit;
    @Value("${app.ai-global-daily-limit:100}") private int globalLimit;
    public AiQuotaService(AiQuotaRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.repository = repository; this.jdbc=jdbc; }
    @Override public void run(String... args) {
        if (!repository.existsById("global")) {
            try { jdbc.update("insert into ai_quota (id, day_window, day_count, minute_window, minute_count) values ('global',0,0,0,0)"); }
            catch (org.springframework.dao.DataIntegrityViolationException concurrentStartup) {
                if (!repository.existsById("global")) throw concurrentStartup;
            }
        }
    }
    private void reset(AiQuota quota, long now) {
        long day = Math.floorDiv(now, 86400000);
        long minute = Math.floorDiv(now, 60000);
        if (quota.dayWindow != day) { quota.dayWindow = day; quota.dayCount = 0; }
        if (quota.minuteWindow != minute) { quota.minuteWindow = minute; quota.minuteCount = 0; }
    }
    @Transactional
    public boolean acquire(long userId) {
        AiQuota global = repository.lock("global").orElseThrow(() -> new IllegalStateException("AI quota not initialized"));
        AiQuota user = repository.findById("user:" + userId).orElseGet(() -> new AiQuota("user:" + userId));
        long now = System.currentTimeMillis(); reset(global, now); reset(user, now);
        if (userLimit <= 0 || globalLimit <= 0 || user.dayCount >= userLimit || global.dayCount >= globalLimit || user.minuteCount >= 3) return false;
        user.dayCount++; user.minuteCount++; global.dayCount++;
        repository.save(user); repository.save(global);
        return true;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> status(long userId) {
        long now = System.currentTimeMillis();
        AiQuota user = repository.findById("user:"+userId).orElseGet(() -> new AiQuota("user:"+userId));
        AiQuota global = repository.findById("global").orElseGet(() -> new AiQuota("global"));
        // Do not mutate managed entities in a read-only transaction.
        int used = user.dayWindow == Math.floorDiv(now,86400000) ? user.dayCount : 0;
        int total = global.dayWindow == Math.floorDiv(now,86400000) ? global.dayCount : 0;
        return Map.of("remaining", Math.max(0,userLimit-used), "globalAvailable", globalLimit>total,
                "limit", Math.max(0,userLimit), "resetAt", (Math.floorDiv(now,86400000)+1)*86400000,
                "note", "按UTC自然日重置；失败请求也计入，重启不会恢复额度");
    }
}
