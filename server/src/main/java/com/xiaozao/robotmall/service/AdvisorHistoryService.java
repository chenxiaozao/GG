package com.xiaozao.robotmall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.AdvisorConversation;
import com.xiaozao.robotmall.repository.AdvisorConversationRepository;
import com.xiaozao.robotmall.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只保存登录账号的有效选购对话，供本人跨设备查看。 */
@Service
public class AdvisorHistoryService {
    private static final int LIMIT = 40;
    private final AdvisorConversationRepository conversations;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public record Message(String role, String content, String sentAt) {}

    /** 注入账号、对话数据和 JSON 转换器。 */
    public AdvisorHistoryService(
            AdvisorConversationRepository conversations,
            UserRepository users,
            ObjectMapper mapper) {
        this.conversations = conversations;
        this.users = users;
        this.mapper = mapper;
    }

    /** 读取本人最近的消息。 */
    public List<Message> list(long userId) {
        var record = conversations.findById(userId).orElse(null);
        if (record == null) return List.of();
        try {
            return mapper.readValue(record.messagesJson, new TypeReference<List<Message>>() {});
        } catch (JsonProcessingException e) {
            throw new BizException("咨询记录暂时无法读取");
        }
    }

    /** 追加一问一答，保留最近二十轮，保存前遮盖常见联系方式。 */
    @Transactional
    public void append(long userId, String question, String answer) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        var messages = new ArrayList<>(list(userId));
        String now = LocalDateTime.now().toString();
        messages.add(new Message("user", AdvisorScope.redactContacts(question), now));
        String clean = AdvisorScope.redactContacts(answer);
        messages.add(
                new Message("assistant", clean.substring(0, Math.min(clean.length(), 2000)), now));
        var record = new AdvisorConversation();
        record.userId = userId;
        try {
            record.messagesJson =
                    mapper.writeValueAsString(
                            messages.subList(
                                    Math.max(0, messages.size() - LIMIT), messages.size()));
        } catch (JsonProcessingException e) {
            throw new BizException("咨询记录暂时无法保存");
        }
        conversations.save(record);
    }

    /** 清空本人咨询，不改变浏览历史或偏好画像。 */
    @Transactional
    public void clear(long userId) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        conversations.deleteById(userId);
    }
}
