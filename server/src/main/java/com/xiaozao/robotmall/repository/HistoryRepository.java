package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.History;

/** 提供账号浏览记录的持久化查询与保存。 */
public interface HistoryRepository extends JpaRepository<History, Long> {
    /** 按用户编号查询对应记录。 */
    List<History> findByUserId(Long userId);
}
