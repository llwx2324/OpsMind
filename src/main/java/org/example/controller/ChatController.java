package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.domain.dto.ChatRequest;
import org.example.domain.dto.ClearRequest;
import org.example.domain.po.ChatMessage;
import org.example.domain.po.ChatSession;
import org.example.domain.vo.ApiResponse;
import org.example.domain.vo.ChatResponse;
import org.example.domain.vo.SessionInfoResponse;
import org.example.domain.vo.SseMessage;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天及 AI-Ops 接口控制器。
 *
 * <p>负责对外暴露聊天相关的 HTTP 与 SSE 接口，包括同步问答、SSE 实时流式问答、清理会话、查询会话信息
 * 以及触发 AI-Ops 多 Agent 协同分析流程。控制器本身不承载业务逻辑，主要职责为：
 *  1) 解析 HTTP 请求并进行合法性校验；
 *  2) 构建或委托 ChatService / AiOpsService 创建模型、Agent 与工具回调；
 *  3) 管理内存级会话（ChatSession）生命周期；
 *  4) 使用 SseEmitter 将流式模型输出以 SSE 事件发送给前端。
 *
 * 注意事项：
 *  - 本控制器使用内存 Map(`sessions`)保存会话状态，仅适用于单实例或开发环境；生产环境应将会话持久化或共享到外部存储（如 Redis）。
 *  - SSE 相关方法使用异步线程池执行模型交互，需注意线程池容量与下游模型调用的超时边界，避免资源耗尽。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 同步聊天接口（阻塞式）。
     *
     * <p>使用 DashScope 模型和 ReactAgent 执行一次完整的问答，会等待模型返回完整答案后再响应。
     * 适用于客户端需要完整回答后一次性渲染的场景。
     *
     * <p>旧实现已迁移到不可访问的兼容命名空间，正式请求由持久化会话控制器处理。</p>
     *
     * @param request 包含会话 id 与用户问题的请求对象（@see ChatRequest）
     * @return 包含模型回答的标准 ApiResponse（成功或错误信息封装在 ChatResponse 中）
     */
    @PostMapping("/legacy-disabled/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("收到聊天请求 - 会话 ID：{}，问题：{}", request.getId(), request.getQuestion());

            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            ChatSession session = getOrCreateSession(request.getId());
            List<ChatMessage> history = session.getHistory();
            logger.info("聊天历史对数：{}", history.size() / 2);

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            chatService.logAvailableTools();

            String systemPrompt = chatService.buildSystemPrompt(history);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            String fullAnswer = chatService.executeChat(agent, request.getQuestion());

            session.addMessage(request.getQuestion(), fullAnswer);
            logger.info("已更新聊天历史 - 会话 ID：{}，消息对数：{}",
                    request.getId(), session.getMessagePairCount());

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));
        } catch (Exception e) {
            logger.error("聊天失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 清理会话历史。
     *
     * <p>根据请求中提供的会话 id 清空内存中的会话历史。该接口不删除会话对象本身，仅清除消息列表。
     * 当请求的 session id 为空或不存在时会返回相应的错误信息。
     *
     * <p>旧实现已迁移到不可访问的兼容命名空间，避免绕过新版身份与所有权校验。</p>
     *
     * @param request 包含要清理的 sessionId 的请求对象（@see ClearRequest）
     * @return 操作结果描述（成功或失败原因）
     */
    @PostMapping("/legacy-disabled/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            logger.info("收到清理聊天历史请求 - 会话 ID：{}", request.getId());

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话 ID 不能为空"));
            }

            ChatSession session = sessions.get(request.getId());
            if (session != null) {
                session.clearHistory();
                logger.info("聊天历史已清理 - 会话 ID：{}", request.getId());
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            }
            return ResponseEntity.ok(ApiResponse.error("会话不存在"));
        } catch (Exception e) {
            logger.error("清理聊天历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * SSE 流式聊天接口（非阻塞）。
     *
     * <p>该接口使用 Server-Sent Events 向客户端实时推送模型的增量输出。流程：
     *  1) 验证请求并创建 SseEmitter；
     *  2) 在独立线程中构建模型、创建 Agent 并订阅返回的流；
     *  3) 将流式内容按 chunk 发送为多个 SSE 事件，最终发送 done 事件并完成连接。
     *
     * 注意：
     *  - 若客户端或中间代理断开连接，SseEmitter 可能抛出异常，系统会在 catch 块中安静地完成（completeWithErrorQuietly）。
     *  - 本实现将会话保存在内存中（sessions），并在流完成后将完整答案追加到会话历史。
     *
     * <p>旧实现已迁移到不可访问的兼容命名空间，正式 SSE 请求由新版持久化链路处理。</p>
     *
     * @param request 包含会话 id 与用户问题的请求对象（@see ChatRequest）
     * @return SseEmitter 用于在同一 HTTP 连接上持续发送事件
     */
    @PostMapping(value = "/legacy-disabled/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                completeWithErrorQuietly(emitter, e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try {
                logger.info("收到流式聊天请求 - 会话 ID：{}，问题：{}",
                        request.getId(), request.getQuestion());

                ChatSession session = getOrCreateSession(request.getId());
                List<ChatMessage> history = session.getHistory();
                logger.info("流式聊天历史对数：{}", history.size() / 2);

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                chatService.logAvailableTools();

                String systemPrompt = chatService.buildSystemPrompt(history);
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                StringBuilder fullAnswerBuilder = new StringBuilder();
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                stream.subscribe(
                        output -> handleStreamingOutput(output, fullAnswerBuilder, emitter),
                        error -> handleStreamingError(error, emitter),
                        () -> handleStreamingComplete(request, session, fullAnswerBuilder, emitter)
                );
            } catch (Exception e) {
                logger.error("流式聊天初始化失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (Exception ex) {
                    logger.error("发送流式错误消息失败", ex);
                }
                completeWithErrorQuietly(emitter, e);
            }
        });

        return emitter;
    }

    /**
     * 触发 AI-Ops 多 Agent 协同分析流程并以 SSE 实时输出分析进度与最终报告。
     *
     * <p>该接口用于将 Prometheus 告警、日志与内部文档交给 AiOps 服务执行 Planner/Executor/Supervisor 等角色
     * 的协同流程，过程中的中间结果会以多条 SSE 事件推送给前端，最后尝试抽取并输出一份结构化的告警分析报告。
     *
     * 注意：该接口会创建较长时间的模型会话并与外部工具交互，应确保请求的超时时间和服务配额适当。
     *
     * @return SseEmitter 用于在同一 HTTP 连接上持续发送 AI-Ops 执行日志与最终报告
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        // 创建一个超时为 10 分钟的 SseEmitter 流式响应对象，适用于长时间的 AI-Ops 执行流程
        SseEmitter emitter = new SseEmitter(600000L);

        // 在独立线程中执行 AI-Ops 流程，避免阻塞主线程
        executor.execute(() -> {
            try {
                logger.info("收到 AI-Ops 请求");

                // 创建 DashScopeApi 和 ChatModel，用于 AI-Ops 流程的多 Agent 协同分析
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createChatModel(dashScopeApi, 0.3, 8000, 0.9);
                // 获取 MCP 工具
                ToolCallback[] toolCallbacks = chatService.getToolCallbacks();

                // 发送初始消息给前端，提示正在读取告警并拆解任务
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.content("正在读取告警并拆解任务...\n"), MediaType.APPLICATION_JSON));

                // 执行 AI-Ops 分析流程，返回可能的 OverAllState（包含 Planner/Executor/Supervisor 的执行状态信息）
                // Optional 表示可能返回空
                Optional<OverAllState> overAllStateOptional =
                        aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);

                // 如果 AI-Ops 流程未返回有效结果，则发送错误消息并结束 SSE 连接
                if (overAllStateOptional.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 编排未返回有效结果"),
                                    MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                // AI-Ops 流程返回有效结果，获取 OverAllState
                OverAllState state = overAllStateOptional.get();
                logger.info("AI-Ops 编排完成，正在提取最终报告");

                // 尝试从 OverAllState 中提取最终报告文本
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    logger.info("已提取最终报告，长度：{}", finalReportText.length());

                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("告警分析报告\n\n"), MediaType.APPLICATION_JSON));

                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(finalReportText.substring(i, end)), MediaType.APPLICATION_JSON));
                    }

                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                } else {
                    logger.warn("无法提取最终 AI-Ops 报告");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("AI Ops 流程已完成，但未生成最终报告。"),
                                    MediaType.APPLICATION_JSON));
                }

                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();
                logger.info("AI-Ops 请求已完成");
            } catch (Exception e) {
                logger.error("AI-Ops 编排失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (Exception ex) {
                    logger.error("发送 AI-Ops 错误消息失败", ex);
                }
                completeWithErrorQuietly(emitter, e);
            }
        });

        return emitter;
    }

    /**
     * 获取会话信息（只读）。
     *
     * <p>返回指定 sessionId 的元信息，包括创建时间和已保存的问答对数。该接口不会返回会话的完整消息内容
     *（仅用于展示会话概览）。
     *
     * <p>旧实现已迁移到不可访问的兼容命名空间，防止读取未按用户隔离的内存会话。</p>
     *
     * @param sessionId 会话唯一标识
     * @return 会话元信息或不存在提示
     */
    @GetMapping("/legacy-disabled/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - 会话 ID：{}", sessionId);

            ChatSession session = sessions.get(sessionId);
            if (session != null) {
                SessionInfoResponse response = new SessionInfoResponse();
                response.setSessionId(sessionId);
                response.setMessagePairCount(session.getMessagePairCount());
                response.setCreateTime(session.getCreateTime());
                return ResponseEntity.ok(ApiResponse.success(response));
            }
            return ResponseEntity.ok(ApiResponse.error("会话不存在"));
        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 处理来自 Agent 的流式输出节点并通过 SSE 发送给客户端。
     *
     * <p>该方法负责解析不同类型的 NodeOutput：模型流输出、模型完成、工具调用完成与 hook 完成。
     * 对于模型增量输出（AGENT_MODEL_STREAMING），将增量文本追加到 fullAnswerBuilder 并立即通过 SSE 发送。
     *
     * @param output 来自 Agent 的输出节点
     * @param fullAnswerBuilder 用于累积完整答案的 StringBuilder
     * @param emitter SSE 事件发送器
     */
    private void handleStreamingOutput(NodeOutput output, StringBuilder fullAnswerBuilder, SseEmitter emitter) {
        try {
            if (output instanceof StreamingOutput streamingOutput) {
                OutputType type = streamingOutput.getOutputType();

                if (type == OutputType.AGENT_MODEL_STREAMING) {
                    String chunk = streamingOutput.message().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        fullAnswerBuilder.append(chunk);
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                        logger.info("已发送流式内容块");
                    }
                } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                    logger.info("模型输出完成");
                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                    logger.info("工具调用完成：{}", output.node());
                } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                    logger.debug("钩子完成：{}", output.node());
                }
            }
        } catch (Exception e) {
            logger.error("发送流式消息失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理流式交互中的异常并通过 SSE 返回错误信息，随后关闭 emitter。
     *
     * @param error 发生的异常
     * @param emitter SSE 事件发送器
     */
    private void handleStreamingError(Throwable error, SseEmitter emitter) {
        logger.error("流式聊天失败", error);
        try {
            emitter.send(SseEmitter.event().name("message")
                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            logger.error("发送流式错误消息失败", ex);
        }
        completeWithErrorQuietly(emitter, error);
    }

    /**
     * 流式交互完成回调：保存完整答案到会话并发送 done 事件。
     *
     * @param request 原始请求对象（包含问题与会话 id）
     * @param session 当前会话对象
     * @param fullAnswerBuilder 累积的完整答案文本
     * @param emitter SSE 事件发送器
     */
    private void handleStreamingComplete(ChatRequest request, ChatSession session,
                                         StringBuilder fullAnswerBuilder, SseEmitter emitter) {
        try {
            String fullAnswer = fullAnswerBuilder.toString();
            logger.info("流式聊天已完成 - 会话 ID：{}，答案长度：{}",
                    request.getId(), fullAnswer.length());

            session.addMessage(request.getQuestion(), fullAnswer);
            logger.info("已更新聊天历史 - 会话 ID：{}，消息对数：{}",
                    request.getId(), session.getMessagePairCount());

            emitter.send(SseEmitter.event().name("message")
                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            logger.error("发送流式完成消息失败", e);
            completeWithErrorQuietly(emitter, e);
        }
    }

    /**
     * 尝试以错误结束 SseEmitter，若 emitter 已完成则静默忽略 IllegalStateException。
     *
     * @param emitter SSE 事件发送器
     * @param error 触发完成的异常
     */
    private void completeWithErrorQuietly(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (IllegalStateException e) {
            logger.debug("SSE 发射器已完成", e);
        }
    }

    /**
     * 获取或创建会话对象。
     *
     * <p>如果传入的 sessionId 为空，则生成新的 UUID 作为会话 id。会话对象保存在内存 map 中，
     * 并由 ChatSession 构造函数初始化创建时间与历史记录容器。
     *
     * @param sessionId 可选的会话 id
     * @return 存在或新建的 ChatSession
     */
    private ChatSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, ChatSession::new);
    }
}
