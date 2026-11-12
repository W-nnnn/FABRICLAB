package com.fabriclab.admin.model;

/**
 * 后台登录后的最小身份：仅承载会话展示所需字段。
 * 不携带密码散列——角色与启用状态每次请求查库复核，会话内身份不足以放行。
 */
public record AdminUser(Long id, String username, String displayName) {
}
