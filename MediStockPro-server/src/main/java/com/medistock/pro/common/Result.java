package com.medistock.pro.common;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一响应体 (对齐 V2.0 API 设计: code/message/data/traceId/timestamp)
 */
@Data
public class Result<T> implements Serializable {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private LocalDateTime timestamp;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = LocalDateTime.now();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>("SUCCESS", "success", data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}
