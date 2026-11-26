package com.xiaozao.robotmall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.AdvisorConversation;
import com.xiaozao.robotmall.entity.AdvisorThread;
import com.xiaozao.robotmall.repository.AdvisorConversationRepository;
import com.xiaozao.robotmall.repository.AdvisorThreadRepository;
import com.xiaozao.robotmall.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

/** 只保存登录账号的有效选购对话，供本人跨设备查看。 */
@Service
public class AdvisorHistoryService {
    private static final int LIMIT = 40;
    private static final int THREAD_LIMIT = 50;
    public static final String LEGACY = "legacy";
    private final AdvisorConversationRepository conversations;
    private final AdvisorThreadRepository threads;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public record Message(String role, String content, String sentAt) {}

    /** 左侧列表只返回标题与时间，不返回咨询正文。 */
    public record ThreadSummary(String id, String title, String updatedAt) {}

    /** 加载某个会话需要恢复的内容。 */
    public record ThreadDetail(
            ThreadSummary thread, List<Message> messages, List<Long> contextProductIds) {}

    /** 注入账号、对话数据和 JSON 转换器。 */
    public AdvisorHistoryService(
            AdvisorConversationRepository conversations,
            AdvisorThreadRepository threads,
            UserRepository users,
            ObjectMapper mapper) {
        this.conversations = conversations;
        this.threads = threads;
        this.users = users;
        this.mapper = mapper;
    }

    /** 读取本人最近的消息。 */
    public List<Message> list(long userId) {
        var record = conversations.findById(userId).orElse(null);
        if (record == null) return List.of();
        return read(record.messagesJson);
    }

    /** 解析数据库保存的问答，不信任客户端拼接的历史。 */
    private List<Message> read(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<Message>>() {});
        } catch (JsonProcessingException e) {
            throw new BizException("咨询记录暂时无法读取");
        }
    }

    /** 追加一问一答，保留最近二十轮，保存前遮盖常见联系方式。 */
    @Transactional
    public void append(long userId, String question, String answer) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        var record = new AdvisorConversation();
        record.userId = userId;
        record.messagesJson = updatedMessages(list(userId), question, answer);
        conversations.save(record);
    }

    /** 追加一轮并统一限制消息容量。 */
    private String updatedMessages(List<Message> history, String question, String answer) {
        var messages = new ArrayList<>(history);
        String now = LocalDateTime.now().toString();
        messages.add(new Message("user", AdvisorScope.redactContacts(question), now));
        String clean = AdvisorScope.redactContacts(answer);
        messages.add(
                new Message("assistant", clean.substring(0, Math.min(clean.length(), 2000)), now));
        try {
            return mapper.writeValueAsString(
                    messages.subList(Math.max(0, messages.size() - LIMIT), messages.size()));
        } catch (JsonProcessingException e) {
            throw new BizException("咨询记录暂时无法保存");
        }
    }

    /** 列出本人会话，旧版咨询保留为单独一项。 */
    @Transactional(readOnly = true)
    public List<ThreadSummary> summaries(long userId) {
        var result =
                new ArrayList<>(
                        threads.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                                .map(this::summary)
                                .toList());
        var legacy = list(userId);
        if (!legacy.isEmpty()) result.add(legacySummary(legacy));
        return result;
    }

    /** 新建会话并保存从商品页带来的候选，不继承其他线程。 */
    @Transactional
    public ThreadSummary create(long userId, Collection<Long> candidates) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        if (threads.countByUserId(userId) >= THREAD_LIMIT)
            throw new BizException("最多保留 50 个对话，请先删除不需要的对话");
        var thread = new AdvisorThread();
        thread.id = UUID.randomUUID().toString();
        thread.userId = userId;
        thread.updatedAt = LocalDateTime.now();
        thread.candidateIds =
                candidates.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
        return summary(threads.save(thread));
    }

    /** 读取本人指定会话；旧数据仍从旧表读取，不移动或覆盖。 */
    public ThreadDetail detail(long userId, String id) {
        if (LEGACY.equals(id)) {
            var messages = list(userId);
            if (messages.isEmpty()) throw missing();
            return new ThreadDetail(legacySummary(messages), messages, List.of());
        }
        var thread = owned(userId, id);
        return new ThreadDetail(
                summary(thread),
                read(thread.messagesJson),
                thread.candidateIds.isBlank()
                        ? List.of()
                        : Arrays.stream(thread.candidateIds.split(","))
                                .map(Long::valueOf)
                                .toList());
    }

    /** 保存一轮到当前会话，第一条有效问题自动生成标题。 */
    @Transactional
    public void append(long userId, String id, String question, String answer) {
        if (id == null || LEGACY.equals(id)) {
            append(userId, question, answer);
            return;
        }
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        var thread = owned(userId, id);
        List<Message> history = read(thread.messagesJson);
        if (history.isEmpty()) {
            String clean = AdvisorScope.redactContacts(question).replaceAll("\\s+", " ").strip();
            int end =
                    clean.offsetByCodePoints(
                            0, Math.min(28, clean.codePointCount(0, clean.length())));
            thread.title = clean.substring(0, end);
        }
        thread.updatedAt = LocalDateTime.now();
        thread.messagesJson = updatedMessages(history, question, answer);
    }

    /** 仅清除当前会话的商品候选，保留咨询正文。 */
    @Transactional
    public void clearCandidates(long userId, String id) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        owned(userId, id).candidateIds = "";
    }

    /** 只删除选中的会话，不影响其他会话和商品浏览记录。 */
    @Transactional
    public void delete(long userId, String id) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        if (LEGACY.equals(id)) conversations.deleteById(userId);
        else threads.delete(owned(userId, id));
    }

    /** 转成列表项，不暴露内部账号字段。 */
    private ThreadSummary summary(AdvisorThread thread) {
        return new ThreadSummary(thread.id, thread.title, thread.updatedAt.toString());
    }

    /** 将旧版一条长对话显示为可单独恢复的会话。 */
    private ThreadSummary legacySummary(List<Message> messages) {
        return new ThreadSummary(LEGACY, "以前的咨询", messages.get(messages.size() - 1).sentAt());
    }

    /** 查询不到本人会话和查询别人的会话使用同一个结果。 */
    private AdvisorThread owned(long userId, String id) {
        return threads.findByIdAndUserId(id, userId).orElseThrow(this::missing);
    }

    /** 返回统一的会话不存在提示。 */
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或已删除");
    }

    /** 清空本人咨询，不改变浏览历史或偏好画像。 */
    @Transactional
    public void clear(long userId) {
        users.findForUpdate(userId).orElseThrow(() -> new BizException("用户不存在"));
        conversations.deleteById(userId);
    }
}
