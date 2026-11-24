package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.ShoppingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 提供选购计划的持久化查询与保存。 */
public interface ShoppingPlanRepository extends JpaRepository<ShoppingPlan, Long> {
    /** 按更新时间倒序读取指定用户的计划。 */
    List<ShoppingPlan> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
