package com.medistock.pro.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        log.warn("业务异常: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> notLogin(NotLoginException e) {
        return Result.error(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> notPermission(NotPermissionException e) {
        return Result.error(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> duplicateKey(DuplicateKeyException e) {
        // P0: 单号/编码唯一索引冲突 = 重复提交, 由业务层捕获后返回原单; 漏网之鱼在此兜底
        log.warn("唯一约束冲突: {}", e.getMessage());
        return Result.error(ErrorCode.DUPLICATE_KEY);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> bind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return Result.error(ErrorCode.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> error(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
