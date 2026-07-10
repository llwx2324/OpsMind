package org.example.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import io.reactivex.Flowable;
import jakarta.annotation.PostConstruct;
import org.example.domain.po.ChatMessage;
import org.example.domain.vo.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    /**
     * 向量检索服务，用于根据查询获取 Top-K 相关文档。
     */
    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${dashscope.api.key}")
    private String apiKey;

    /**
     * RAG 检索时返回的 top-k 文档数量，默认 3。该值影响上下文信息量与模型负载。
     */
    @Value("${rag.top-k:3}")
    private int topK;

    /**
     * 所使用的生成模型名称，来自配置，可能影响输出质量与成本。
     */
    @Value("${rag.model:qwen3-30b-a3b-thinking-2507}")
    private String model;

    private Generation generation;

    @PostConstruct
    public void init() {
        Constants.apiKey = apiKey;
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        generation = new Generation();
        logger.info("RAG 服务初始化完成，model: {}, topK: {}", model, topK);
    }

    /**
     * 基于检索增强生成（RAG）的流式查询入口（无历史）。
     *
     * @param question 用户问题
     * @param callback 回调用于接收检索结果与模型流式块
     */
    public void queryStream(String question, StreamCallback callback) {
        queryStream(question, new ArrayList<>(), callback);
    }

    /**
     * 基于检索增强生成（RAG）的流式查询入口。
     *
     * <p>流程：
     * <ol>
     *   <li>调用向量检索获取 topK 文档</li>
     *   <li>将检索结果拼接为上下文并构建 prompt</li>
     *   <li>调用生成模型的流式接口并通过回调逐块返回结果</li>
     * </ol>
     * </p>
     *
     * @param question 用户问题
     * @param history  可选的会话历史，用于模型上下文
     * @param callback 回调用于接收检索结果、推理过程与最终文本
     */
    public void queryStream(String question, List<ChatMessage> history, StreamCallback callback) {
        try {
            logger.info("收到 RAG 流式查询: {}", question);

            List<SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(question, topK);
            callback.onSearchResults(searchResults);

            if (searchResults.isEmpty()) {
                logger.warn("未找到相关文档");
                callback.onComplete("抱歉，我在知识库中没有找到相关信息来回答您的问题。", "");
                return;
            }

            String context = buildContext(searchResults);
            String prompt = buildPrompt(question, context);
            generateAnswerStream(prompt, history, callback);
        } catch (Exception e) {
            logger.error("RAG 流式查询失败", e);
            callback.onError(e);
        }
    }

    /**
     * 将搜索结果格式化为模型提示词可以使用的上下文块。
     *
     * @param searchResults 检索到的文档列表
     * @return 供模型使用的上下文字符串（包含编号与内容）
     */
    private String buildContext(List<SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            context.append("【参考资料").append(i + 1).append("】\n");
            context.append(result.getContent()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * 构建最终发送给生成模型的 prompt。
     *
     * @param question 用户问题
     * @param context  由检索结果拼接而成的上下文
     * @return 最终 prompt 字符串
     */
    private String buildPrompt(String question, String context) {
        return String.format(
                "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n" +
                        "参考资料：\n%s\n" +
                        "用户问题：%s\n\n" +
                        "请基于上述参考资料给出准确、详细的回答。如果参考资料中没有相关信息，请明确说明。",
                context, question
        );
    }

    /**
     * 调用底层生成模型（支持流式输出）并通过回调逐块返回内容。
     *
     * <p>关键点：
     * <ul>
     *   <li>将历史消息转换为 Message 列表以保留对话上下文</li>
     *   <li>配置 GenerationParam 的 incrementalOutput=true 以启用流式输出</li>
     *   <li>在接收流时即时回调 onContentChunk，以便上层可将片段透传给前端</li>
     * </ul>
     * </p>
     *
     * @param prompt   发送给模型的 prompt
     * @param history  会话历史
     * @param callback 回调用于接收流式内容与完成事件
     */
    private void generateAnswerStream(String prompt, List<ChatMessage> history, StreamCallback callback)
            throws NoApiKeyException, ApiException, InputRequiredException {

        List<Message> messages = new ArrayList<>();

        for (ChatMessage historyMsg : history) {
            String role = historyMsg.getRole();
            String content = historyMsg.getContent();

            if ("user".equals(role)) {
                messages.add(Message.builder()
                        .role(Role.USER.getValue())
                        .content(content)
                        .build());
            } else if ("assistant".equals(role)) {
                messages.add(Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(content)
                        .build());
            }
        }

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        messages.add(userMsg);

        logger.debug("发送给 AI 模型的消息数量: {}（包含 {} 条历史消息）", messages.size(), history.size());

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .incrementalOutput(true)
                .resultFormat("message")
                .messages(messages)
                .build();

        logger.info("开始调用 AI 模型流式接口...");
        Flowable<GenerationResult> result = generation.streamCall(param);

        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder finalContent = new StringBuilder();

        logger.info("开始接收 AI 模型流式响应...");
        result.blockingForEach(message -> {
            if (message.getOutput() != null &&
                    message.getOutput().getChoices() != null &&
                    !message.getOutput().getChoices().isEmpty()) {
                String content = message.getOutput().getChoices().get(0).getMessage().getContent();

                if (content != null && !content.isEmpty()) {
                    logger.debug("收到 AI 模型内容块: {}", content);
                    finalContent.append(content);
                    callback.onContentChunk(content);
                } else {
                    logger.debug("收到空内容块，跳过");
                }
            }
        });

        logger.info("AI 模型流式响应完成，总内容长度: {}", finalContent.length());
        callback.onComplete(finalContent.toString(), reasoningContent.toString());
    }

    /**
     * RAG 流式回调接口：上层可实现该接口以获取检索结果、模型推理过程中的增量数据以及最终输出或异常。
     */
    public interface StreamCallback {
        /**
         * 检索结果回调（同步返回检索到的文档列表）。
         */
        void onSearchResults(List<SearchResult> results);

        /**
         * 推理过程中推送的推理/链路性说明块（如中间推理过程），当前实现中未使用但保留扩展点。
         */
        void onReasoningChunk(String chunk);

        /**
         * 模型生成的内容增量，每接收到一块内容即回调，适合前端实时渲染。
         */
        void onContentChunk(String chunk);

        /**
         * 推理完成回调，包含完整内容与（可选）完整的推理说明。
         */
        void onComplete(String fullContent, String fullReasoning);

        /**
         * 出错回调。
         */
        void onError(Exception e);
    }
}
