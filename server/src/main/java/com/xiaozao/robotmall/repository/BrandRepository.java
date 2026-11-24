package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Brand;

/** 提供商品品牌的持久化查询与保存。 */
public interface BrandRepository extends JpaRepository<Brand, Long> {}
