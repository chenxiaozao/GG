package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Favorite;

/** 提供账号商品收藏及备注的持久化查询与保存。 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    /** 按用户编号查询对应记录。 */
    List<Favorite> findByUserId(Long userId);

    /** 通过账号及商品编号定位记录。 */
    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);
}
