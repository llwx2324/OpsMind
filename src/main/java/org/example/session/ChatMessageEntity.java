package org.example.session;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
/** 持久化的会话消息，记录流式回答状态、会话内顺序及请求幂等标识。 */
public class ChatMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)") private String sessionId;
    /** 会话内严格递增的消息序号。 */
    @Column(name = "sequence_no", nullable = false) private long sequenceNo;
    /** 客户端幂等键；同一会话内用于识别重复提交。 */
    @Column(name = "request_id") private String requestId;
    @Column(nullable = false) private String role;
    @Column(columnDefinition = "MEDIUMTEXT") private String content;
    /** 助手消息的生成状态；只有 COMPLETED 消息可进入后续模型上下文。 */
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "VARCHAR(16)") private ChatMessageStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    protected ChatMessageEntity() {}
    public ChatMessageEntity(String sessionId, long sequenceNo, String requestId, String role, String content, ChatMessageStatus status, Instant now) {
        this.sessionId = sessionId; this.sequenceNo = sequenceNo; this.requestId = requestId; this.role = role; this.content = content; this.status = status; this.createdAt = now;
    }
    /** 将助手消息原子地转换为已完成状态。 */
    public void complete(String content, Instant now) { this.content = content; this.status = ChatMessageStatus.COMPLETED; this.completedAt = now; }
    /** 追加本轮批量刷新的流式内容片段。 */
    public void append(String chunk) { this.content = (content == null ? "" : content) + chunk; }
    /** 将未完成回答标记为取消或失败，防止其进入后续上下文。 */
    public void fail(ChatMessageStatus status, Instant now) { this.status = status; this.completedAt = now; }
    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public long getSequenceNo() { return sequenceNo; }
    public String getRequestId() { return requestId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public ChatMessageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
