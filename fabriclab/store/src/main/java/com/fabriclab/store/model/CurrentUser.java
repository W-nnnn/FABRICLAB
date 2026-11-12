package com.fabriclab.store.model;

/**
 * 登录后的最小身份：仅承载会话展示与归属校验所需字段。
 * 不携带密码散列与角色状态原文——角色与启用状态每次请求查库复核（FR-U-02）。
 */
public record CurrentUser(Long id, String username, String displayName) {
}
