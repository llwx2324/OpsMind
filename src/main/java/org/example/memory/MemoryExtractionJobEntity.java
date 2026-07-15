package org.example.memory;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memory_extraction_job")
/** 可重试的长期记忆异步提取任务，以会话消息范围保证任务幂等。 */
public class MemoryExtractionJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)") private String sessionId;
    /** 待提取消息范围的起始序号。 */
    @Column(name = "from_sequence", nullable = false) private long fromSequence;
    /** 待提取消息范围的结束序号。 */
    @Column(name = "to_sequence", nullable = false) private long toSequence;
    /** 任务状态，用于重启补偿和失败重试。 */
    @Column(nullable = false) private String status;
    /** 已执行次数，为受限重试提供依据。 */
    @Column(nullable = false) private int attempts;
    /** 执行提取任务的模型版本，便于审计结果变化。 */
    @Column(name = "model_version") private String modelVersion;
    /** 规范化提取结果哈希，用于结果幂等。 */
    @Column(name = "result_hash", columnDefinition = "CHAR(64)") private String resultHash;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MemoryExtractionJobEntity() {}
    public MemoryExtractionJobEntity(String sessionId, long from, long to, Instant now) { this.sessionId=sessionId; this.fromSequence=from; this.toSequence=to; this.status="PENDING"; this.createdAt=now; this.updatedAt=now; }
    public String getSessionId() { return sessionId; }
    public Long getId() { return id; }
    public long getFromSequence() { return fromSequence; }
    public long getToSequence() { return toSequence; }
    public String getStatus() { return status; }
    /** 将任务标记为完成并保存结果哈希。 */
    public void complete(String hash, Instant now) { status="COMPLETED"; resultHash=hash; updatedAt=now; }
    /** 保存失败状态和脱敏错误摘要，供后续补偿处理。 */
    public void fail(String error, Instant now) { status="FAILED"; lastError=error; updatedAt=now; }
}
