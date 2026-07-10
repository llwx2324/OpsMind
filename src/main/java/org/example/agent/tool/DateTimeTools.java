package org.example.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日期时间工具类。查询当前日期和时间
 *
 * <p>提供获取当前日期和时间的能力，通常作为 Agent 可调用的工具注入到提示词执行流程中，
 * 用于回答时间相关问题或将当前时间上下文传给模型。</p>
 */
@Component
public class DateTimeTools {
    
    /**
     * 工具名常量，用于动态构建提示词。
     */
    public static final String TOOL_GET_CURRENT_DATETIME = "getCurrentDateTime";
    
    /**
     * 获取当前用户时区下的日期时间。
     *
     * <p>使用 LocaleContextHolder 获取当前请求绑定的时区，以保证返回结果与用户所在时区一致。</p>
     *
     * @return 当前日期时间字符串
     */
    @Tool(description = "Get the current date and time in the user's timezone")
    public String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
