package com.xiaozao.robotmall.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.AfterSaleRequest;

/** 提供售后申请与处理状态的持久化查询与保存。 */
public interface AfterSaleRequestRepository extends JpaRepository<AfterSaleRequest, Long> {
    /** 按用户编号查询对应记录。 */
    List<AfterSaleRequest> findByUserId(Long userId);

    /** 按订单明细编号查售后申请。 */
    List<AfterSaleRequest> findByOrderItemId(Long orderItemId);

    /** 在事务内锁定指定记录以串行更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AfterSaleRequest request where request.id = :id")
    Optional<AfterSaleRequest> findForUpdate(@Param("id") Long id);
}
