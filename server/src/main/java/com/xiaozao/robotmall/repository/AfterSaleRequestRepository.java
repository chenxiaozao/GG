package com.xiaozao.robotmall.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.AfterSaleRequest;

public interface AfterSaleRequestRepository extends JpaRepository<AfterSaleRequest, Long> {
    List<AfterSaleRequest> findByUserId(Long userId);
    List<AfterSaleRequest> findByOrderItemId(Long orderItemId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AfterSaleRequest request where request.id = :id")
    Optional<AfterSaleRequest> findForUpdate(@Param("id") Long id);
}
