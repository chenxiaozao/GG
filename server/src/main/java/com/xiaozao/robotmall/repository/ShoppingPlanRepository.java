package com.xiaozao.robotmall.repository;
import com.xiaozao.robotmall.entity.ShoppingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ShoppingPlanRepository extends JpaRepository<ShoppingPlan,Long> {
 List<ShoppingPlan> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
