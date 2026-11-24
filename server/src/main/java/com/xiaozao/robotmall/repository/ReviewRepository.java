package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Review;

/** 提供已购商品评价的持久化查询与保存。 */
public interface ReviewRepository extends JpaRepository<Review, Long> {
    /** 读取指定商品允许展示的评价。 */
    List<Review> findByProductIdAndVisibleTrue(Long productId);

    /** 按用户编号查询对应记录。 */
    List<Review> findByUserId(Long userId);
}
