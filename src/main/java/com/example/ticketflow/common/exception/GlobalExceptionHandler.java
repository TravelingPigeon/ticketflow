package com.example.ticketflow.common.exception;

import com.example.ticketflow.common.api.ApiResponse;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>所有分支都通过 {@link #failure(ErrorCode, String)} 构造响应：HTTP 状态码和响应里的
 * {@code code} 都取自同一个 {@link ErrorCode} 常量。这样"状态码"和"错误码"不会各说各话——
 * 把某个码的状态从 400 改成 422，只需要动枚举一处。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        return failure(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception
    ) {
        return failure(ErrorCode.INTERNAL_ERROR, "服务暂时不可用");
    }

    /**
     * 请求体对象里的字段校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return failure(ErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * 方法参数上的约束失败，最常见的场景是 {@code List<@Valid Dto>} 这种<b>容器元素</b>校验——
     * 数组里某一项不合法时抛的是它，而不是 {@link MethodArgumentNotValidException}。
     *
     * <p>没有这个处理器时它会掉进兜底分支返回 500，把一个明确的参数错误伪装成服务端故障。</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        String message = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("请求参数不合法");

        return failure(ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return failure(ErrorCode.FORBIDDEN, "没有权限执行此操作");
    }

    /**
     * 请求体根本解析不出来（JSON 语法错误等）。
     *
     * <p>这是客户端错误，必须是 400 而不是 500——和"未知路径"那条同理：
     * 让"调用方写错了"混进 5xx，告警就失去意义。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException exception
    ) {
        return failure(ErrorCode.VALIDATION_ERROR, "请求体格式不正确");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return failure(ErrorCode.UNAUTHENTICATED, "请先登录");
    }

    /**
     * 请求的路径没有对应的接口。
     *
     * <p>它不是业务错误，但必须是 404 而不是 500——否则"客户端写错 URL"会被当成服务端故障，
     * 在生产环境里污染 5xx 告警。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception
    ) {
        return failure(ErrorCode.ENDPOINT_NOT_FOUND, "接口不存在");
    }

    /**
     * 统一构造错误响应。
     *
     * <p>提示消息由调用方提供（同一个错误码在不同场景下措辞不同），状态码和错误码则一律来自枚举。</p>
     */
    private ResponseEntity<ApiResponse<Void>> failure(
            ErrorCode errorCode,
            String message
    ) {
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), message));
    }
}
