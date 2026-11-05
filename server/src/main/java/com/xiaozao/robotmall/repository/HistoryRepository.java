package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.History;

public interface HistoryRepository extends JpaRepository<History,Long> {
    List<History> findByUserId(Long userId);
}