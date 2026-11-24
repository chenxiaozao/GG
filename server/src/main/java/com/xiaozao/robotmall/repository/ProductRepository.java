package com.xiaozao.robotmall.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.Product;

/** 提供商品库存、价格和规格的持久化查询与保存。 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    /** 在事务内锁定指定记录以串行更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from Product item where item.id = :id")
    Optional<Product> findForUpdate(@Param("id") Long id);
}
