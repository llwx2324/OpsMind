package org.example.session;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_session")
/** 持久化聊天会话，保存身份归属、压缩进度、记忆进度及乐观锁版本。 */
public class ChatSessionEntity {
    /** 会话 UUID。 */
    @Id @Column(columnDefinition = "CHAR(36)") private String id;
    /** 会话所属租户；必须与 userId 一起作为访问过滤条件。 */
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    /** 会话创建者；会话不会自动在同租户用户之间共享。 */
    @Column(name = "user_id", nullable = false) private String userId;
    private String title;
    /** 对旧消息前缀生成的压缩摘要。 */
    @Column(name = "compact_summary", columnDefinition = "MEDIUMTEXT") private String compactSummary;
    /** 摘要已覆盖的最大消息序号。 */
    @Column(name = "compacted_through_sequence", nullable = false) private long compactedThroughSequence;
    /** 已分配的最大消息序号，用于生成严格递增的会话内序号。 */
    @Column(name = "last_assigned_sequence", nullable = false) private long lastAssignedSequence;
    /** 已提交长期记忆提取任务的最大消息序号。 */
    @Column(name = "memory_extracted_through_sequence", nullable = false) private long memoryExtractedThroughSequence;
    /** 最终并发校验版本，避免 Redis 锁失效后覆盖较新的会话状态。 */
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected ChatSessionEntity() {}
    public ChatSessionEntity(String id, String tenantId, String userId, Instant now, Instant expiresAt) {
        this.id = id; this.tenantId = tenantId; this.userId = userId; this.createdAt = now; this.updatedAt = now; this.expiresAt = expiresAt;
    }
    /** 分配并返回下一个会话内消息序号。 */
    public long nextSequence() { return ++lastAssignedSequence; }
    /** 刷新会话活跃时间并延长统一过期时间。 */
    public void touch(Instant now, Instant expiresAt) { this.updatedAt = now; this.expiresAt = expiresAt; }
    /** 清除会话派生状态并重置序号，供兼容清空接口使用。 */
    public void clear(Instant now, Instant expiresAt) { compactSummary = null; compactedThroughSequence = 0; lastAssignedSequence = 0; memoryExtractedThroughSequence = 0; touch(now, expiresAt); }
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompactSummary() { return compactSummary; }
    public void setCompactSummary(String compactSummary) { this.compactSummary = compactSummary; }
    public long getCompactedThroughSequence() { return compactedThroughSequence; }
    public void setCompactedThroughSequence(long value) { compactedThroughSequence = value; }
    public long getLastAssignedSequence() { return lastAssignedSequence; }
    public long getMemoryExtractedThroughSequence() { return memoryExtractedThroughSequence; }
    public void setMemoryExtractedThroughSequence(long value) { memoryExtractedThroughSequence = value; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
