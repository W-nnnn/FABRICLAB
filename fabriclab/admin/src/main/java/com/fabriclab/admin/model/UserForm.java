package com.fabriclab.admin.model;

/**
 * 账号表单：只有显示名、邮箱、电话可提交。用户名与角色作为只读上下文字段回显，
 * 服务层不接受其变更（提交了不同值即拒绝），避免越权改身份。
 */
public record UserForm(Long id, String username, String role, String displayName,
                       String email, String phone) {
}
