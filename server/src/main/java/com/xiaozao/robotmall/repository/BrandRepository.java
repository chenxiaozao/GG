package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Brand;

public interface BrandRepository extends JpaRepository<Brand,Long> {
}