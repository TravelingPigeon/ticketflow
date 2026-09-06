package com.example.ticketflow.common.api;

/**
 * Unified response envelope for API results.
 *
 * @param success whether the request succeeded
 * @param code    machine-readable result code
 * @param message human-readable result message
 * @param data    response payload
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
