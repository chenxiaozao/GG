package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.ShoppingPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 提供选购计划历史快照的持久化查询与保存。 */
public interface ShoppingPlanVersionRepository extends JpaRepository<ShoppingPlanVersion, Long> {
    /** 按版本编号倒序读取计划快照。 */
    List<ShoppingPlanVersion> findByPlanIdOrderByIdDesc(Long planId);
}
