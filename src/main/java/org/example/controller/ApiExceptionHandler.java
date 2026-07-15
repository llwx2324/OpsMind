package org.example.controller;

import org.example.domain.vo.ApiResponse;
import org.example.session.SessionNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/** 将会话与身份相关异常转换为稳定、无敏感信息的 API 错误响应。 */
public class ApiExceptionHandler {
    /** 会话不存在或不属于当前用户时统一返回 404。 */
    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("SESSION_NOT_FOUND"));
    }

    /** 身份缺失、声明冲突或越权时返回 403。 */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("FORBIDDEN"));
    }

    /** MySQL 乐观锁冲突时提示客户端当前会话繁忙。 */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("SESSION_BUSY"));
    }

    /** 兜底隐藏内部异常细节，避免将提示词、消息或 token 写入响应。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("INTERNAL_ERROR"));
    }
}
