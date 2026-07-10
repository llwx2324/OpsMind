package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一 API 响应包装类。
 *
 * <p>用于前后端接口统一返回结构，包含状态码、消息与业务数据三部分。
 * success / error 工厂方法用于简化 Controller 层组装响应。</p>
 */
@Getter
@Setter
public class ApiResponse<T> {

    /**
     * 响应状态码，200 表示成功，500 表示业务失败或异常。
     */
    private int code;

    /**
     * 响应消息，通常为 success 或错误描述。
     */
    private String message;

    /**
     * 业务数据。
     */
    private T data;

    /**
     * 创建成功响应。
     *
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    /**
     * 创建失败响应。
     *
     * @param message 错误信息
     * @param <T> 数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }
}
