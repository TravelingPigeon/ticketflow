package com.example.ticketflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 错误码目录。
 *
 * <p>每个错误码绑定它的 HTTP 状态码：不再是"所有业务错误一律 400"，而是由错误码本身决定
 * （见 {@link GlobalExceptionHandler}）。这样接口契约才和错误语义对得上：
 * 资源不存在是 404、唯一键冲突是 409、请求体引用了不存在的对象是 422。</p>
 *
 * <p>提示消息**不放在这里**：同一个错误码在不同场景下的措辞本来就不一样
 * （{@code TICKET_NOT_FOUND} 可能是"工单不存在"，也可能是"工单不存在或不属于当前租户"），
 * 所以消息仍然由抛出点提供。</p>
 *
 * <p>错误码字符串就是枚举名（{@link #code()} 直接返回 {@code name()}），
 * 这样"码"和"枚举常量"不可能对不上——少一处可以写错的地方。</p>
 */
public enum ErrorCode {

    // ---- 400：请求本身不合法 ----
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_PAGE(HttpStatus.BAD_REQUEST),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST),
    DEMO_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_SLA_POLICY(HttpStatus.BAD_REQUEST),

    // ---- 401：没通过认证 ----
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    // ---- 403：认证过了但被拒绝 ----
    USER_LOCKED(HttpStatus.FORBIDDEN),
    CUSTOMER_LOCKED(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),

    // ---- 404：要操作的对象不存在 ----
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),

    // ---- 409：与当前数据状态冲突（唯一键、状态机、并发）----
    TENANT_CODE_EXISTS(HttpStatus.CONFLICT),
    USERNAME_EXISTS(HttpStatus.CONFLICT),
    CUSTOMER_EMAIL_EXISTS(HttpStatus.CONFLICT),
    TICKET_NO_EXISTS(HttpStatus.CONFLICT),
    ROLE_CODE_EXISTS(HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    TICKET_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    TICKET_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    LAST_ROLE_MANAGER(HttpStatus.CONFLICT),

    // ---- 422：请求体里引用的对象不存在 ----
    ASSIGNEE_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_PERMISSION(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_ROLE_CODE(HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- 500：没有预料到的失败 ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public String code() {
        return name();
    }

    public HttpStatus status() {
        return status;
    }
}
