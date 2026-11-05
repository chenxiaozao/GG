package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Category;

public interface CategoryRepository extends JpaRepository<Category,Long> {
}