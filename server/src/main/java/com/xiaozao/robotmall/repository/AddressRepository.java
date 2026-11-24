package com.xiaozao.robotmall.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.xiaozao.robotmall.entity.Address;

/** 提供账号收货地址的持久化查询与保存。 */
public interface AddressRepository extends JpaRepository<Address, Long> {
    /** 按用户编号查询对应记录。 */
    List<Address> findByUserId(Long userId);
}
