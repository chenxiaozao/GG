package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.CartItem;

/** 提供购物车明细的持久化查询与保存。 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    /** 按用户编号查询对应记录。 */
    List<CartItem> findByUserId(Long userId);

    /** 通过账号及商品编号定位记录。 */
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
}
