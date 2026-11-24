package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.AccountAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 提供后台账号操作审计的持久化查询与保存。 */
public interface AccountAuditRepository extends JpaRepository<AccountAudit, Long> {
    /** 按时间倒序取目标账号最近五十条审计。 */
    List<AccountAudit> findTop50ByTargetIdOrderByCreatedAtDesc(Long targetId);
}
