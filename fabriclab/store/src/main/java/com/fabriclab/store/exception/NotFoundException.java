package com.fabriclab.store.exception;

/** 资源不存在：映射 HTTP 404 并给出可理解提示，如分类不可用。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
