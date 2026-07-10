package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.domain.po.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天服务。
 *
 * <p>封装与模型、方法工具（method tools）和 MCP 工具回调的拼装逻辑，负责：
 * <ul>
 *   <li>创建 DashScope API / ChatModel 实例</li>
 *   <li>基于会话历史构建系统提示词（system prompt）</li>
 *   <li>将方法工具与外部 MCP 工具注入到 ReactAgent</li>
 *   <li>执行同步对话（非流式）并返回完整回答</li>
 * </ul>
 * </p>
 *
 * <p>注意：该 Service 不处理 HTTP 层的异常映射，Controller 层负责捕获并组装 API 响应。</p>
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    /**
     * 可选的日志查询方法工具，仅在 Mock 或测试环境下注册。
     * <p>生产环境可能由外部 MCP 提供日志查询能力，因此将该依赖设置为可选注入。</p>
     */
    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    /**
     * MCP 提供的工具回调提供者；在未启用 MCP 时可能为 null。
     */
    @Autowired(required = false)
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String chatModelName;

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel。
     *
     * @param dashScopeApi DashScopeApi 实例
     * @param temperature  控制随机性（0.0-1.0），越低越确定
     * @param maxToken     最大输出 token 数，影响回答长度上限
     * @param topP         nucleus 采样参数，控制采样分布
     * @return 配置好的 DashScopeChatModel
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(chatModelName)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（使用工程默认参数）。
     *
     * @return 默认配置的 DashScopeChatModel
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）。
     *
     * <p>该提示词包含：基础系统说明、可用方法工具说明以及按时间/角色拼接的对话历史。
     * 将历史直接加入提示词有助于 Agent 理解上下文，但需注意提示词长度可能随会话增长而增加，
     * 在高并发或长期会话场景应在上层对历史进行裁剪以避免超出模型上下文窗口。</p>
     *
     * @param history 历史消息列表（按时间升序）
     * @return 完整的系统提示词字符串，用于传递给 ReactAgent
     */
    public String buildSystemPrompt(List<ChatMessage> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();
        
        // 基础系统提示
        systemPromptBuilder.append("你是 OpsMind 智能运维助手，一个专业的企业智能运维助手，可以获取当前时间、查询天气信息、搜索内部文档知识库，以及查询 Prometheus 告警信息。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用 getCurrentDateTime 工具。\n");
        systemPromptBuilder.append("当用户需要查询公司内部文档、流程、最佳实践或技术指南时，使用 queryInternalDocs 工具。\n");
        systemPromptBuilder.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");
        systemPromptBuilder.append("当用户需要查询腾讯云日志时，请调用腾讯云mcp服务查询,默认查询地域ap-guangzhou,查询时间范围为近一个月。\n\n");
        
        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (ChatMessage msg : history) {
                String role = msg.getRole();
                String content = msg.getContent();
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }
        
        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");
        
        return systemPromptBuilder.toString();
    }

    /**
     * 动态构建方法工具数组（method tools）。主要是自己封装的工具类
     *
     * <p>当本地注册了 QueryLogsTools（通常用于测试/Mock 环境）时会将其包含在方法工具中，
     * 否则仅返回生产环境下应有的工具集合。</p>
     *
     * @return 方法工具对象数组，供 ReactAgent.methodTools 使用
     */
    public Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            // Mock 模式：包含 QueryLogsTools
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        } else {
            // 真实模式：不包含 QueryLogsTools（由 MCP 提供日志查询功能）
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
        }
    }

    /**
     * 获取外部 MCP 提供的工具回调列表。
     *
     * ToolCallback ： Spring AI 中对“工具”的统一封装接口
     * yml 配置中配置了 mcp 即可自动将 mcp 工具包装为 ToolCallback，本地 @Tool 方法不会自动被包装为 ToolCallback。
     *
     * @return ToolCallback 数组，若 MCP 未启用则返回长度为 0 的数组
     */
    public ToolCallback[] getToolCallbacks() {
        if (tools == null) {
            logger.info("MCP 工具未启用，跳过外部工具回调");
            return new ToolCallback[0];
        }
        return tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表，便于启动时观察 Agent 可调用的外部能力。
     */
    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = getToolCallbacks();
        logger.info("可用 MCP 工具列表:");
        if (toolCallbacks.length == 0) {
            logger.info(">>> 无外部 MCP 工具");
            return;
        }
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建并配置 ReactAgent 实例。
     *
     * @param chatModel    聊天模型实例
     * @param systemPrompt 系统提示词（包含方法工具说明与历史上下文）
     * @return 配置好的 ReactAgent，包含方法工具与 MCP 工具回调
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("opsmind_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray()) // 加载自定义方法工具
                .tools(getToolCallbacks()) // 获取 MCP 工具回调
                .build();
    }

    /**
     * 执行 ReactAgent 对话（非流式）。
     *
     * <p>该方法会调用 agent.call 并返回最终的文本回答。注意：agent.call 可能触发方法工具或外部工具调用，
     * 这些调用的异常或超时会向上抛出 GraphRunnerException，由调用方处理。</p>
     *
     * @param agent    ReactAgent 实例（必须已包含所需工具回调）
     * @param question 用户问题文本
     * @return AI 返回的文本回答
     * @throws GraphRunnerException 当 Agent 执行或工具调用失败时抛出
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
