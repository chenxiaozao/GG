package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.AiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** 提供人工智能调用计数的持久化查询与保存。 */
public interface AiQuotaRepository extends JpaRepository<AiQuota, String> {
    /** 锁定指定额度行以同步计数。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AiQuota q where q.id = :id")
    Optional<AiQuota> lock(@Param("id") String id);
}
