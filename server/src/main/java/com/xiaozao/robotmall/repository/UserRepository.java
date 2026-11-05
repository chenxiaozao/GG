package com.xiaozao.robotmall.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.xiaozao.robotmall.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from User item where item.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);
    Optional<User> findByUsername(String username);
    Optional<User> findByToken(String token);
}
