package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.AiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AiQuotaRepository extends JpaRepository<AiQuota, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AiQuota q where q.id = :id")
    Optional<AiQuota> lock(@Param("id") String id);
}
