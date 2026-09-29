package com.medistock.pro.common;

import lombok.Getter;

/**
 * 业务错误码 (库存引擎 INV_001~007 对齐 V3.0 库存引擎设计 §9)
 */
@Getter
public enum ErrorCode {

    // 通用
    PARAM_INVALID("COMMON_400", "参数校验失败"),
    UNAUTHORIZED("COMMON_401", "未登录或登录已过期"),
    FORBIDDEN("COMMON_403", "无操作权限"),
    NOT_FOUND("COMMON_404", "资源不存在"),
    DUPLICATE_KEY("COMMON_409", "重复提交或编码冲突"),
    SYSTEM_ERROR("COMMON_500", "系统异常"),

    // 认证
    AUTH_FAILED("AUTH_001", "用户名或密码错误"),
    ACCOUNT_DISABLED("AUTH_002", "账号已停用"),

    // 库存引擎 (V3.0 §9)
    INV_001("INV_001", "库存不足"),
    INV_002("INV_002", "版本冲突"),
    INV_003("INV_003", "锁定量不足"),
    INV_004("INV_004", "批次不可用(冻结/过期/报废)"),
    INV_005("INV_005", "重复请求"),
    INV_006("INV_006", "来源单据状态非法"),
    INV_007("INV_007", "批次/效期校验失败"),

    // 采购
    SUP_001("SUP_001", "供应商资质过期或缺失, 禁止新增采购订单");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
