package org.example.controller;

import org.example.domain.dto.ChatRequest;
import org.example.domain.dto.ClearRequest;
import org.example.domain.vo.ApiResponse;
import org.example.domain.vo.ChatResponse;
import org.example.domain.vo.SessionInfoResponse;
import org.example.security.CallerIdentity;
import org.example.security.CurrentIdentityService;
import org.example.session.ChatMessageStatus;
import org.example.session.ChatSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** v1 临时兼容适配器，统一复用 v2 服务并在响应中声明弃用信息。 */
@RestController
@RequestMapping("/api")
public class LegacyPersistentChatController {
    private final PersistentChatController v2;
    private final CurrentIdentityService identities;
    private final ChatSessionService sessions;
    public LegacyPersistentChatController(PersistentChatController v2, CurrentIdentityService identities, ChatSessionService sessions) { this.v2=v2; this.identities=identities; this.sessions=sessions; }

    @PostMapping("/chat")
    /** 同步聊天兼容入口，保留旧请求体并附加弃用响应头。 */
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        ResponseEntity<ApiResponse<ChatResponse>> response = v2.legacyChat(request, requestId);
        return ResponseEntity.status(response.getStatusCode()).headers(h -> { h.add("Deprecation", "true"); h.add("Sunset", "Migrate to /api/v2/chat before the next release"); }).body(response.getBody());
    }

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    /** SSE 聊天兼容入口，内部复用 v2 的持久化和锁管理链路。 */
    public ResponseEntity<SseEmitter> stream(@RequestBody ChatRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return deprecated(ResponseEntity.ok(v2.legacyStream(request, requestId)));
    }

    @PostMapping("/chat/clear")
    /** 清空当前用户拥有的旧版会话。 */
    public ResponseEntity<ApiResponse<String>> clear(@RequestBody ClearRequest request) {
        sessions.clear(request.getId(), identities.requireIdentity());
        return deprecated(ResponseEntity.ok(ApiResponse.success("会话历史已清空")));
    }

    @GetMapping("/chat/session/{sessionId}")
    /** 查询旧版会话统计信息，仍执行完整租户与用户所有权校验。 */
    public ResponseEntity<ApiResponse<SessionInfoResponse>> info(@PathVariable String sessionId) {
        CallerIdentity identity = identities.requireIdentity();
        var session = sessions.requireOwned(sessionId, identity);
        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(session.getId());
        response.setCreateTime(session.getCreatedAt().toEpochMilli());
        response.setMessagePairCount((int) sessions.listMessages(sessionId, identity).stream().filter(m -> "user".equals(m.getRole()) && m.getStatus() == ChatMessageStatus.COMPLETED).count());
        return deprecated(ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * 在保留原响应状态、响应头和响应体的同时附加 v1 弃用信息。
     *
     * @param response v2 兼容调用产生的原始响应
     * @param <T> 响应体类型
     * @return 带 Deprecation 和 Sunset 响应头的新响应
     */
    private <T> ResponseEntity<T> deprecated(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode()).headers(headers -> {
            headers.putAll(response.getHeaders());
            headers.add("Deprecation", "true");
            headers.add("Sunset", "Migrate to /api/v2/chat before the next release");
        }).body(response.getBody());
    }
}
