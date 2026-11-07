package com.xiaozao.robotmall.repository;
import com.xiaozao.robotmall.entity.AccountAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AccountAuditRepository extends JpaRepository<AccountAudit,Long> {
    List<AccountAudit> findTop50ByTargetIdOrderByCreatedAtDesc(Long targetId);
}
