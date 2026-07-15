package org.example.session;

import org.example.config.ChatProperties;
import org.example.domain.po.ChatMessage;
import org.example.security.CallerIdentity;
import org.example.memory.MemoryExtractionJobRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
/**
 * 会话与消息持久化的核心服务，统一执行所有权校验、幂等处理和上下文重建。
 *
 * <p>控制器不得绕过本服务直接按裸 sessionId 访问用户数据。</p>
 */
public class ChatSessionService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final SessionContextCache cache;
    private final ChatProperties properties;
    private final MemoryExtractionJobRepository extractionJobs;

    public ChatSessionService(ChatSessionRepository sessions, ChatMessageRepository messages, SessionContextCache cache, ChatProperties properties, MemoryExtractionJobRepository extractionJobs) {
        this.sessions = sessions; this.messages = messages; this.cache = cache; this.properties = properties; this.extractionJobs = extractionJobs;
    }

    @Transactional
    /**
     * 为当前调用者创建一个新会话。
     *
     * @param identity 当前调用者身份
     * @return 已持久化的会话
     */
    public ChatSessionEntity create(CallerIdentity identity) {
        Instant now = Instant.now();
        return sessions.save(new ChatSessionEntity(UUID.randomUUID().toString(), identity.tenantId(), identity.userId(), now, expiry(now)));
    }

    @Transactional(readOnly = true)
    /**
     * 查询当前调用者拥有的会话。
     *
     * @throws SessionNotFoundException 会话不存在或属于其他租户、用户时抛出
     */
    public ChatSessionEntity requireOwned(String sessionId, CallerIdentity identity) {
        return sessions.findByIdAndTenantIdAndUserId(sessionId, identity.tenantId(), identity.userId())
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @Transactional
    /**
     * 接受一轮请求，持久化用户消息并创建 PENDING 助手消息。
     *
     * @param sessionId 会话 ID
     * @param identity 当前调用者身份
     * @param requestId 请求幂等键
     * @param question 用户问题
     * @param allowImplicitCreate 是否允许兼容 v1 的隐式建会话
     * @return 本轮持久化句柄；重复请求会返回既有消息
     */
    public TurnHandle startTurn(String sessionId, CallerIdentity identity, String requestId, String question, boolean allowImplicitCreate) {
        ChatSessionEntity session = sessions.findByIdAndTenantIdAndUserId(sessionId, identity.tenantId(), identity.userId()).orElseGet(() -> {
            if (!allowImplicitCreate) throw new SessionNotFoundException(sessionId);
            Instant now = Instant.now();
            return sessions.save(new ChatSessionEntity(sessionId, identity.tenantId(), identity.userId(), now, expiry(now)));
        });
        if (requestId != null && !requestId.isBlank()) {
            var existing = messages.findBySessionIdAndRequestId(sessionId, requestId);
            if (existing.isPresent()) {
                // 幂等重试复用相邻的助手消息，不再分配序号或重复调用模型。
                ChatMessageEntity assistant = messages.findBySessionIdAndSequenceNo(sessionId, existing.get().getSequenceNo() + 1).orElse(null);
                return TurnHandle.duplicate(session, existing.get(), assistant);
            }
        }
        Instant now = Instant.now();
        session.touch(now, expiry(now));
        if (session.getTitle() == null || session.getTitle().isBlank()) session.setTitle(title(question));
        ChatMessageEntity user = messages.save(new ChatMessageEntity(sessionId, session.nextSequence(), requestId, "user", question, ChatMessageStatus.COMPLETED, now));
        ChatMessageEntity assistant = messages.save(new ChatMessageEntity(sessionId, session.nextSequence(), null, "assistant", "", ChatMessageStatus.PENDING, now));
        sessions.save(session);
        cache.evict(sessionId);
        return new TurnHandle(session, user, assistant, false);
    }

    @Transactional
    /** 将本轮助手消息标记为完成并刷新会话及上下文缓存。 */
    public void complete(TurnHandle handle, String answer) {
        ChatMessageEntity assistant = messages.findById(handle.assistant().getId()).orElseThrow();
        assistant.complete(answer == null ? "" : answer, Instant.now());
        touch(handle.session());
        cache.evict(handle.session().getId());
    }

    @Transactional
    /** 批量持久化一段流式回答内容。 */
    public void append(TurnHandle handle, String chunk) {
        ChatMessageEntity assistant = messages.findById(handle.assistant().getId()).orElseThrow();
        assistant.append(chunk);
        messages.save(assistant);
    }

    @Transactional
    /** 将流式助手消息标记为 CANCELLED 或 FAILED。 */
    public void fail(TurnHandle handle, ChatMessageStatus status) {
        ChatMessageEntity assistant = messages.findById(handle.assistant().getId()).orElseThrow();
        assistant.fail(status, Instant.now());
        touch(handle.session());
        cache.evict(handle.session().getId());
    }

    @Transactional(readOnly = true)
    /**
     * 组装“摘要 + 摘要之后的已完成消息”，缓存未命中时从 MySQL 重建。
     *
     * @return 可安全用于后续模型请求的上下文
     */
    public ChatContext context(String sessionId, CallerIdentity identity) {
        requireOwned(sessionId, identity);
        return cache.get(sessionId).orElseGet(() -> {
            ChatSessionEntity session = requireOwned(sessionId, identity);
            List<ChatMessage> history = messages.findBySessionIdAndStatusAndSequenceNoGreaterThanOrderBySequenceNoAsc(sessionId, ChatMessageStatus.COMPLETED, session.getCompactedThroughSequence()).stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent())).toList();
            ChatContext result = new ChatContext(session.getCompactSummary(), history);
            cache.put(sessionId, result);
            return result;
        });
    }

    @Transactional
    /** 清除会话消息和派生状态，但保留会话本身。 */
    public void clear(String sessionId, CallerIdentity identity) {
        ChatSessionEntity session = requireOwned(sessionId, identity);
        messages.deleteBySessionId(sessionId);
        extractionJobs.deleteBySessionId(sessionId);
        session.clear(Instant.now(), expiry(Instant.now()));
        sessions.save(session);
        cache.evict(sessionId);
    }

    @Transactional
    /** 删除当前用户拥有的会话，并使对应上下文缓存失效。 */
    public void delete(String sessionId, CallerIdentity identity) {
        ChatSessionEntity session = requireOwned(sessionId, identity);
        sessions.delete(session);
        cache.evict(sessionId);
    }

    @Transactional(readOnly = true)
    /** 按最近更新时间倒序列出当前用户会话。 */
    public List<ChatSessionEntity> list(CallerIdentity identity, int limit) {
        return sessions.findByTenantIdAndUserIdOrderByUpdatedAtDesc(identity.tenantId(), identity.userId(), PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    @Transactional(readOnly = true)
    /** 使用更新时间与 ID 复合游标分页列出当前用户会话。 */
    public List<ChatSessionEntity> list(CallerIdentity identity, int limit, Instant beforeUpdatedAt, String beforeId) {
        if (beforeUpdatedAt != null && (beforeId == null || beforeId.isBlank())) throw new IllegalArgumentException("beforeId is required with beforeUpdatedAt");
        return sessions.findOwnerPage(identity.tenantId(), identity.userId(), beforeUpdatedAt, beforeId,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    @Transactional(readOnly = true)
    /** 查询当前用户会话的全部持久化消息。 */
    public List<ChatMessageEntity> listMessages(String sessionId, CallerIdentity identity) {
        requireOwned(sessionId, identity);
        return messages.findBySessionIdOrderBySequenceNoAsc(sessionId);
    }

    @Transactional(readOnly = true)
    /** 按消息序号游标查询一页消息，并以正序返回给客户端。 */
    public List<ChatMessageEntity> listMessages(String sessionId, CallerIdentity identity, long beforeSequence, int limit) {
        requireOwned(sessionId, identity);
        List<ChatMessageEntity> page = messages.findBySessionIdAndSequenceNoLessThanOrderBySequenceNoDesc(
                sessionId, beforeSequence, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
        java.util.Collections.reverse(page);
        return page;
    }

    @Transactional
    /** 物理删除已超过保留期的会话，相关消息与记忆任务由外键级联清理。 */
    public void purgeExpired() { sessions.deleteByExpiresAtBefore(Instant.now()); }

    private void touch(ChatSessionEntity session) { session.touch(Instant.now(), expiry(Instant.now())); sessions.save(session); }
    private Instant expiry(Instant now) { return now.plus(properties.getRetentionDays(), ChronoUnit.DAYS); }
    private String title(String question) { return question.length() <= 30 ? question : question.substring(0, 30) + "..."; }

    /**
     * 一轮对话的持久化句柄。
     *
     * @param session 所属会话
     * @param user 已完成的用户消息
     * @param assistant 待完成或既有的助手消息
     * @param duplicate 是否为幂等重试
     */
    public record TurnHandle(ChatSessionEntity session, ChatMessageEntity user, ChatMessageEntity assistant, boolean duplicate) {
        static TurnHandle duplicate(ChatSessionEntity session, ChatMessageEntity user, ChatMessageEntity assistant) { return new TurnHandle(session, user, assistant, true); }
    }
}
