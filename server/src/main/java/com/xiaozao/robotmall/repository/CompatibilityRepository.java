package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Compatibility;

/** 提供主机与配件适配关系的持久化查询与保存。 */
public interface CompatibilityRepository extends JpaRepository<Compatibility, Long> {
    /** 按主机编号查询关联配件。 */
    List<Compatibility> findByRobotProductId(Long robotProductId);

    /** 通过主机和配件编号查询对应适配关系。 */
    Optional<Compatibility> findByRobotProductIdAndAccessoryProductId(
            Long robotProductId, Long accessoryProductId);
}
