package com.fabriclab.store.exception;

/** 业务规则校验失败；由控制器层映射为对应的 HTTP 状态码。field 可选，指向出错字段。 */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        super(message);
        this.field = null;
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** 出错字段名；为空表示错误不属于某个具体字段。 */
    public String field() {
        return field;
    }
}
