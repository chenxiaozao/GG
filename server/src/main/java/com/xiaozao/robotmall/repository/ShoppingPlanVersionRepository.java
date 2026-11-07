package com.xiaozao.robotmall.repository;
import com.xiaozao.robotmall.entity.ShoppingPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ShoppingPlanVersionRepository extends JpaRepository<ShoppingPlanVersion,Long> {
 List<ShoppingPlanVersion> findByPlanIdOrderByIdDesc(Long planId);
}
