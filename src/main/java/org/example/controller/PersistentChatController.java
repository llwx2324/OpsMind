package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.domain.dto.ChatRequest;
import org.example.domain.dto.ClearRequest;
import org.example.domain.vo.*;
import org.example.memory.LongMemoryService;
import org.example.security.CallerIdentity;
import org.example.security.CurrentIdentityService;
import org.example.service.ChatService;
import org.example.session.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/v2/chat")
/**
 * v2 持久化聊天、会话管理与长期记忆 API。
 *
 * <p>调用者身份只从已验证 JWT 获取；模型调用始终处于会话锁生命周期内。</p>
 */
public class PersistentChatController {
    private final CurrentIdentityService identities;
    private final ChatSessionService sessions;
    private final SessionLockService locks;
    private final ChatService chatService;
    private final LongMemoryService memories;
    private final ConversationCompactionService compaction;
    private final MeterRegistry metrics;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    public PersistentChatController(CurrentIdentityService identities, ChatSessionService sessions, SessionLockService locks,
                                    ChatService chatService, LongMemoryService memories, ConversationCompactionService compaction, MeterRegistry metrics) {
        this.identities=identities; this.sessions=sessions; this.locks=locks; this.chatService=chatService; this.memories=memories; this.compaction=compaction; this.metrics=metrics;
    }

    @PostMapping("/sessions")
    /** 为当前调用者创建会话。 */
    public ResponseEntity<ApiResponse<ChatSessionView>> createSession() {
        var session = sessions.create(identities.requireIdentity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toView(session)));
    }

    @GetMapping("/sessions")
    /** 使用稳定游标分页查询当前用户的会话列表。 */
    public ApiResponse<List<ChatSessionView>> listSessions(@RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) java.time.Instant beforeUpdatedAt,
            @RequestParam(required = false) String beforeId) {
        CallerIdentity identity = identities.requireIdentity();
        return ApiResponse.success(sessions.list(identity, limit, beforeUpdatedAt, beforeId).stream().map(this::toView).toList());
    }

    @GetMapping("/sessions/{id}/messages")
    /** 按消息序号游标查询当前用户会话中的持久化消息。 */
    public ApiResponse<List<PersistentChatMessageView>> listMessages(@PathVariable String id,
            @RequestParam(defaultValue = "9223372036854775807") long beforeSequence,
            @RequestParam(defaultValue = "100") int limit) {
        CallerIdentity identity = identities.requireIdentity();
        return ApiResponse.success(sessions.listMessages(id, identity, beforeSequence, limit).stream().map(m -> new PersistentChatMessageView(m.getSequenceNo(), m.getRole(), m.getContent(), m.getStatus().name(), m.getCreatedAt())).toList());
    }

    @DeleteMapping("/sessions/{id}")
    /** 删除当前用户拥有的会话。 */
    public ResponseEntity<Void> deleteSession(@PathVariable String id) { sessions.delete(id, identities.requireIdentity()); return ResponseEntity.noContent().build(); }

    @PostMapping("/clear")
    /** 清空会话消息与派生状态，但保留会话记录。 */
    public ApiResponse<String> clear(@RequestBody ClearRequest request) { sessions.clear(request.getId(), identities.requireIdentity()); return ApiResponse.success("会话历史已清空"); }

    @PostMapping
    /** 执行同步聊天；会话必须已通过 v2 接口显式创建。 */
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return chatInternal(request, requestId, false);
    }

    /** v1 兼容期入口，允许保留旧版隐式创建会话行为。 */
    public ResponseEntity<ApiResponse<ChatResponse>> legacyChat(ChatRequest request, String requestId) {
        return chatInternal(request, requestId, true);
    }

    /**
     * 执行同步聊天主流程，包括参数校验、会话锁、幂等检查、消息落库和后置任务触发。
     *
     * @param request 聊天请求
     * @param requestId 客户端幂等键，可为空
     * @param allowImplicitCreate 是否允许 v1 兼容模式隐式创建会话
     * @return 聊天结果或稳定的忙碌、参数错误响应
     */
    private ResponseEntity<ApiResponse<ChatResponse>> chatInternal(ChatRequest request, String requestId, boolean allowImplicitCreate) {
        if (allowImplicitCreate && (request.getId() == null || request.getId().isBlank())) request.setId(UUID.randomUUID().toString());
        if (request.getQuestion() == null || request.getQuestion().isBlank() || request.getId() == null || request.getId().isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error("sessionId 和 question 不能为空"));
        CallerIdentity identity = identities.requireIdentity();
        try (SessionLockService.LockHandle ignored = locks.tryAcquire(request.getId())) {
            if (ignored == null) { metric("lock_conflict", "sync"); return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("SESSION_BUSY")); }
            ChatContext context = sessions.context(request.getId(), identity);
            ChatSessionService.TurnHandle turn = sessions.startTurn(request.getId(), identity, requestId == null ? UUID.randomUUID().toString() : requestId, request.getQuestion(), allowImplicitCreate);
            if (turn.duplicate()) {
                metric("idempotency_hit", "sync");
                if (turn.assistant() != null && turn.assistant().getStatus() == ChatMessageStatus.COMPLETED)
                    return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(turn.assistant().getContent())));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("SESSION_BUSY"));
            }
            try {
                String answer = ask(context, identity, request.getQuestion());
                sessions.complete(turn, answer);
                metric("completed", "sync");
                memories.queueIfDue(request.getId());
                compaction.compactIfNeeded(request.getId(), identity);
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));
            } catch (Exception e) { sessions.fail(turn, ChatMessageStatus.FAILED); metric("failed", "sync"); return ResponseEntity.internalServerError().body(ApiResponse.success(ChatResponse.error("聊天失败"))); }
        }
    }

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    /** 启动持久化 SSE 聊天，并把后续处理调度到独立线程。 */
    public SseEmitter stream(@RequestBody ChatRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return streamInternal(request, requestId, false);
    }
    /** v1 兼容期 SSE 入口，允许隐式创建会话。 */
    public SseEmitter legacyStream(ChatRequest request, String requestId) { return streamInternal(request, requestId, true); }
    /**
     * 校验 SSE 请求身份并将流式主流程调度到独立线程。
     *
     * @param request 聊天请求
     * @param requestId 客户端幂等键，可为空
     * @param allowImplicitCreate 是否允许 v1 兼容模式隐式创建会话
     * @return 已注册异步处理任务的 SSE emitter
     */
    private SseEmitter streamInternal(ChatRequest request, String requestId, boolean allowImplicitCreate) {
        SseEmitter emitter = new SseEmitter(300000L);
        final CallerIdentity identity;
        try { identity = identities.requireIdentity(); } catch (Exception e) { error(emitter, "UNAUTHORIZED"); return emitter; }
        executor.execute(() -> runStream(request, requestId, identity, allowImplicitCreate, emitter));
        return emitter;
    }

    @GetMapping("/memories")
    /** 分页列出当前用户仍有效的脱敏长期记忆。 */
    public ApiResponse<List<MemoryView>> listMemories(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(memories.list(identities.requireIdentity(), limit).stream().map(m -> new MemoryView(m.getId(), m.getMemoryType(), m.getContent(), m.getExpiresAt())).toList());
    }
    @DeleteMapping("/memories/{id}")
    /** 删除当前用户的记忆并写入防回填 tombstone。 */
    public ResponseEntity<Void> deleteMemory(@PathVariable String id) { memories.delete(id, identities.requireIdentity()); return ResponseEntity.noContent().build(); }

    /**
     * 执行完整 SSE 生命周期，保证取消、失败和完成三个终态互斥，并且会话锁只释放一次。
     *
     * <p>只有完成终态会触发长期记忆提取和上下文压缩；取消或失败消息不会进入后续上下文。</p>
     *
     * @param request 聊天请求
     * @param requestId 客户端幂等键，可为空
     * @param identity 已验证的调用者身份
     * @param allowImplicitCreate 是否允许 v1 兼容模式隐式创建会话
     * @param emitter 当前 HTTP 连接对应的 SSE emitter
     */
    private void runStream(ChatRequest request, String requestId, CallerIdentity identity, boolean allowImplicitCreate, SseEmitter emitter) {
        if (allowImplicitCreate && (request.getId() == null || request.getId().isBlank())) request.setId(UUID.randomUUID().toString());
        if (request.getQuestion() == null || request.getQuestion().isBlank() || request.getId() == null || request.getId().isBlank()) { error(emitter, "sessionId 和 question 不能为空"); return; }
        SessionLockService.LockHandle lock = locks.tryAcquire(request.getId());
        if (lock == null) { metric("lock_conflict", "stream"); error(emitter, "SESSION_BUSY"); return; }
        AtomicBoolean terminal = new AtomicBoolean(false);
        AtomicBoolean lockClosed = new AtomicBoolean(false);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicReference<ChatSessionService.TurnHandle> activeTurn = new AtomicReference<>();
        // timeout、网络错误和正常完成可能并发触发，只允许一个终态负责取消订阅、落库并释放锁。
        Runnable cancel = () -> {
            if (!terminal.compareAndSet(false, true)) return;
            Disposable disposable = subscription.get();
            if (disposable != null) disposable.dispose();
            ChatSessionService.TurnHandle turn = activeTurn.get();
            if (turn != null) try { sessions.fail(turn, ChatMessageStatus.CANCELLED); } catch (Exception ignored) { }
            if (lockClosed.compareAndSet(false, true)) lock.close();
            metric("cancelled", "stream");
        };
        emitter.onTimeout(cancel);
        emitter.onError(ignored -> cancel.run());
        emitter.onCompletion(cancel);
        try {
            ChatContext context = sessions.context(request.getId(), identity);
            ChatSessionService.TurnHandle turn = sessions.startTurn(request.getId(), identity, requestId == null ? UUID.randomUUID().toString() : requestId, request.getQuestion(), allowImplicitCreate);
            if (turn.duplicate()) {
                metric("idempotency_hit", "stream");
                if (turn.assistant() != null && turn.assistant().getStatus() == ChatMessageStatus.COMPLETED) {
                    // 已完成的幂等请求直接重放持久化答案，不再次调用模型。
                    send(emitter, SseMessage.content(turn.assistant().getContent()));
                    send(emitter, SseMessage.done());
                    terminal.set(true);
                    if (lockClosed.compareAndSet(false, true)) lock.close();
                    emitter.complete();
                } else {
                    terminal.set(true);
                    if (lockClosed.compareAndSet(false, true)) lock.close();
                    error(emitter, "SESSION_BUSY");
                }
                return;
            }
            activeTurn.set(turn);
            ReactAgent agent = createAgent(context, identity, request.getQuestion());
            StringBuilder answer = new StringBuilder();
            Flux<NodeOutput> stream = agent.stream(request.getQuestion());
            Disposable disposable = stream.doOnNext(output -> {
                if (output instanceof StreamingOutput value && value.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                    String chunk = value.message().getText();
                    if (chunk != null && !chunk.isBlank()) { answer.append(chunk); sessions.append(turn, chunk); send(emitter, SseMessage.content(chunk)); }
                }
            }).doOnError(failure -> {
                // 终态 CAS 保证错误回调不会覆盖客户端取消或正常完成已经写入的状态。
                if (!terminal.compareAndSet(false, true)) return;
                sessions.fail(turn, ChatMessageStatus.FAILED);
                metric("failed", "stream");
                error(emitter, "聊天失败");
                if (lockClosed.compareAndSet(false, true)) lock.close();
            }).doOnComplete(() -> {
                // 只有赢得终态竞争的回调才能提交完成状态并触发记忆提取与上下文压缩。
                if (!terminal.compareAndSet(false, true)) return;
                sessions.complete(turn, answer.toString());
                metric("completed", "stream");
                memories.queueIfDue(request.getId());
                compaction.compactIfNeeded(request.getId(), identity);
                send(emitter, SseMessage.done());
                if (lockClosed.compareAndSet(false, true)) lock.close();
                emitter.complete();
            }).subscribe();
            subscription.set(disposable);
            if (terminal.get() && !disposable.isDisposed()) disposable.dispose();
        } catch (Exception e) {
            if (terminal.compareAndSet(false, true)) {
                ChatSessionService.TurnHandle turn = activeTurn.get();
                if (turn != null) sessions.fail(turn, ChatMessageStatus.FAILED);
                metric("failed", "stream");
                error(emitter, "聊天失败");
            }
            if (lockClosed.compareAndSet(false, true)) lock.close();
        }
    }

    /**
     * 使用已经隔离的会话上下文执行一次同步模型调用。
     *
     * @param context 已完成所有权校验的会话上下文
     * @param identity 当前调用者身份
     * @param question 当前用户问题
     * @return 模型完整回答
     * @throws IllegalStateException 模型调用失败时抛出
     */
    private String ask(ChatContext context, CallerIdentity identity, String question) {
        ReactAgent agent = createAgent(context, identity, question);
        try {
            return chatService.executeChat(agent, question);
        } catch (Exception e) {
            throw new IllegalStateException("chat model invocation failed", e);
        }
    }
    /**
     * 创建当前请求使用的 Agent，并注入摘要、已完成消息和当前用户范围内召回的记忆。
     *
     * @param context 已完成所有权校验的会话上下文
     * @param identity 当前调用者身份，用于限制长期记忆召回范围
     * @param question 当前问题，用于按需选择记忆
     * @return 带运维工具及安全上下文的 Agent
     */
    private ReactAgent createAgent(ChatContext context, CallerIdentity identity, String question) {
        DashScopeApi api = chatService.createDashScopeApi();
        DashScopeChatModel model = chatService.createStandardChatModel(api);
        return chatService.createReactAgent(model, chatService.buildSystemPrompt(context.history(), context.summary(), memories.recall(identity, question)));
    }
    private ChatSessionView toView(ChatSessionEntity s) { return new ChatSessionView(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt(), s.getExpiresAt()); }
    private void send(SseEmitter emitter, SseMessage message) { try { emitter.send(SseEmitter.event().name("message").data(message, MediaType.APPLICATION_JSON)); } catch (Exception ignored) { } }
    private void error(SseEmitter emitter, String text) { send(emitter, SseMessage.error(text)); emitter.complete(); }
    private void metric(String outcome, String mode) { metrics.counter("opsmind.chat.requests", "outcome", outcome, "mode", mode).increment(); }
}
