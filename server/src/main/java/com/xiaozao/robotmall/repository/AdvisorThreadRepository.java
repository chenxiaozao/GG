package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.AdvisorThread;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** 所有会话都用账号和会话编号共同查询。 */
public interface AdvisorThreadRepository extends JpaRepository<AdvisorThread, String> {
    Optional<AdvisorThread> findByIdAndUserId(String id, Long userId);

    List<AdvisorThread> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
