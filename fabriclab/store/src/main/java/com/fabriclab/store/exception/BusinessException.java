package com.fabriclab.store.exception;

/** 业务规则校验失败；由控制器层映射为对应的 HTTP 状态码。 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
