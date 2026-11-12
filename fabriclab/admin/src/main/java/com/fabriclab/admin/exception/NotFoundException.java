package com.fabriclab.admin.exception;

/**
 * 资源不存在：映射 HTTP 404 并给出可理解提示。
 * 单参构造以提示语同时作为标题与说明；双参构造可分别指定标题与说明。
 */
public class NotFoundException extends RuntimeException {

    private final String title;

    public NotFoundException(String message) {
        super(message);
        this.title = message;
    }

    public NotFoundException(String title, String message) {
        super(message);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
