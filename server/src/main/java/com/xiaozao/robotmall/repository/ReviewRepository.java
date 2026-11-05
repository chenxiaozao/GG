package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Review;

public interface ReviewRepository extends JpaRepository<Review,Long> {
    List<Review> findByProductIdAndVisibleTrue(Long productId);
    List<Review> findByUserId(Long userId);
}