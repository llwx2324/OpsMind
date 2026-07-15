package org.example.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/** 会话消息持久化访问层，所有查询均以已完成所有权校验的 sessionId 为边界。 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findBySessionIdAndStatusOrderBySequenceNoAsc(String sessionId, ChatMessageStatus status);
    List<ChatMessageEntity> findBySessionIdAndStatusAndSequenceNoGreaterThanOrderBySequenceNoAsc(String sessionId, ChatMessageStatus status, long sequenceNo);
    List<ChatMessageEntity> findBySessionIdOrderBySequenceNoAsc(String sessionId);
    List<ChatMessageEntity> findBySessionIdAndSequenceNoLessThanOrderBySequenceNoDesc(String sessionId, long beforeSequence, Pageable pageable);
    /** 按请求幂等键查找既有消息，避免重复创建同一轮对话。 */
    Optional<ChatMessageEntity> findBySessionIdAndRequestId(String sessionId, String requestId);
    Optional<ChatMessageEntity> findBySessionIdAndSequenceNo(String sessionId, long sequenceNo);
    long countBySessionIdAndRoleAndStatus(String sessionId, String role, ChatMessageStatus status);
    void deleteBySessionId(String sessionId);
    /** 查询长期记忆提取任务覆盖的闭区间消息，并保持会话原始顺序。 */
    List<ChatMessageEntity> findBySessionIdAndSequenceNoBetweenOrderBySequenceNoAsc(String sessionId, long from, long to);
}
