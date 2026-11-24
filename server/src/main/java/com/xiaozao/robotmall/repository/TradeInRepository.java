package com.xiaozao.robotmall.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.TradeIn;

/** 提供模拟换购抵扣券的持久化查询与保存。 */
public interface TradeInRepository extends JpaRepository<TradeIn, Long> {
    /** 按用户编号查询对应记录。 */
    List<TradeIn> findByUserId(Long userId);
}
