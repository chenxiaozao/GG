package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.OrderEntity;

/** 提供订单主记录及交易状态的持久化查询与保存。 */
public interface OrderEntityRepository extends JpaRepository<OrderEntity, Long> {
    /** 判断当前账号是否已有使用旧机折价的待付款订单。 */
    boolean existsByUserIdAndStatusAndTradeInCreditIdIsNotNull(Long userId, String status);

    /** 在事务内锁定指定记录以串行更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OrderEntity item where item.id = :id")
    Optional<OrderEntity> findForUpdate(@Param("id") Long id);

    /** 按用户编号查询对应记录。 */
    List<OrderEntity> findByUserId(Long userId);
}
