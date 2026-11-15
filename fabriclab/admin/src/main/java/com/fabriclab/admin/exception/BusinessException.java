package com.fabriclab.admin.exception;

/**
 * 业务规则校验失败；由控制器层映射为 HTTP 400。
 * field 可选，指向出错字段名，用于在表单里就近标红并聚焦，而不是只丢一条顶部提示。
 */
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
