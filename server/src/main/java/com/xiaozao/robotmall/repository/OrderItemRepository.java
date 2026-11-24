package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.OrderItem;

/** 提供订单商品快照的持久化查询与保存。 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    /** 读取指定订单的商品明细。 */
    List<OrderItem> findByOrderId(Long orderId);
}
