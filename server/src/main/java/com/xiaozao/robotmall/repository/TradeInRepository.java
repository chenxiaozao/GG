package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.TradeIn;

public interface TradeInRepository extends JpaRepository<TradeIn,Long> {
    List<TradeIn> findByUserId(Long userId);
}