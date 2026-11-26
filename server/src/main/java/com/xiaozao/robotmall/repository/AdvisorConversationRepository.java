package com.xiaozao.robotmall.repository;

import com.xiaozao.robotmall.entity.AdvisorConversation;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按账号加载最近的选购对话。 */
public interface AdvisorConversationRepository extends JpaRepository<AdvisorConversation, Long> {}
