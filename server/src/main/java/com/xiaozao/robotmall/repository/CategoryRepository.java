package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Category;

/** 提供商品分类的持久化查询与保存。 */
public interface CategoryRepository extends JpaRepository<Category, Long> {}
