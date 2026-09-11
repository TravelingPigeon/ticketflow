package com.example.ticketflow.common.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * 表示调用方没有有效的登录身份。
 *
 * <p>继承 Spring Security 的 {@link AuthenticationException}，
 * 由 {@code GlobalExceptionHandler} 统一映射为 401。</p>
 */
public class UnauthenticatedException extends AuthenticationException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
