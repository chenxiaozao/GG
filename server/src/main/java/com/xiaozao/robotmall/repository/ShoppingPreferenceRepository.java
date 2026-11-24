package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.ShoppingPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** 提供购物画像及授权的持久化查询与保存。 */
public interface ShoppingPreferenceRepository extends JpaRepository<ShoppingPreference, Long> {}
