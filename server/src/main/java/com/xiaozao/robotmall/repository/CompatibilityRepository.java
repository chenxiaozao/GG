package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Compatibility;

public interface CompatibilityRepository extends JpaRepository<Compatibility,Long> {
    List<Compatibility> findByRobotProductId(Long robotProductId);
    Optional<Compatibility> findByRobotProductIdAndAccessoryProductId(Long robotProductId,Long accessoryProductId);
}