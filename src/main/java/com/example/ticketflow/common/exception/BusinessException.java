package com.example.ticketflow.common.exception;

/**
 * 业务异常。
 *
 * <p>持有 {@link ErrorCode} 而不是裸字符串，状态码由错误码决定。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 错误码字符串；保留这个方法，让读它的人不必关心枚举 */
    public String getCode() {
        return errorCode.code();
    }
}