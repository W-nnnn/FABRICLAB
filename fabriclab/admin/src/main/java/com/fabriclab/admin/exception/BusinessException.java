package com.fabriclab.admin.exception;

/** 业务规则校验失败；由控制器层映射为 HTTP 400。 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
