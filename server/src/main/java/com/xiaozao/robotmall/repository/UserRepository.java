package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.User;

/** 提供账号资料、密码散列和会话的持久化查询与保存。 */
public interface UserRepository extends JpaRepository<User, Long> {
    /** 在事务内锁定指定记录以串行更新。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from User item where item.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);

    /** 通过登录名查找账号。 */
    Optional<User> findByUsername(String username);

    /** 通过会话令牌查找账号。 */
    Optional<User> findByToken(String token);
}
