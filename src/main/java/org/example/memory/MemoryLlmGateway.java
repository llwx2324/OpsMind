package org.example.memory;

/** 长期记忆提取、选择和摘要共用的受控大模型调用边界。 */
public interface MemoryLlmGateway {
    /**
     * 使用系统提示词和用户输入执行一次非流式模型调用。
     *
     * @param systemPrompt 约束模型行为的系统提示词
     * @param userPrompt 待处理的业务内容
     * @return 模型文本结果
     */
    String complete(String systemPrompt, String userPrompt);
}
