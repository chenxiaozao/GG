package com.xiaozao.robotmall;

import com.xiaozao.robotmall.service.AiQuotaService;
import com.xiaozao.robotmall.repository.AiQuotaRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:quota_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.ai-user-daily-limit=2","app.ai-global-daily-limit=4"})
class AiQuotaPersistenceTests {
    @Autowired AiQuotaService service;
    @Autowired AiQuotaRepository repository;
    @Test void concurrentCallsCannotOverspendAndStartupCannotResetCounters() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(6);
        try {
            var calls=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<12;i++)calls.add(workers.submit(()->service.acquire(777L)));
            int admitted=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))admitted++;
            assertEquals(2,admitted);
            assertEquals(0,service.status(777L).get("remaining"));
            int global=repository.findById("global").orElseThrow().dayCount;
            service.run();
            assertEquals(global,repository.findById("global").orElseThrow().dayCount);
            assertFalse(service.acquire(777L));
        } finally {workers.shutdownNow();}
    }
}
