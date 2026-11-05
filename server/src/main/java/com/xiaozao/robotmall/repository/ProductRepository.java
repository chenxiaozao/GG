package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.Product;

public interface ProductRepository extends JpaRepository<Product,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from Product item where item.id = :id")
    Optional<Product> findForUpdate(@Param("id") Long id);
}
