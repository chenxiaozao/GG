package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.OrderEntity;

public interface OrderEntityRepository extends JpaRepository<OrderEntity,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OrderEntity item where item.id = :id")
    Optional<OrderEntity> findForUpdate(@Param("id") Long id);
    List<OrderEntity> findByUserId(Long userId);
}
