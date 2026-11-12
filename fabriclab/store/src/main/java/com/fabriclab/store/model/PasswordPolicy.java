package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 注册密码策略：8—32 个字符，且至少包含字母、数字、符号两类。
 * 密码本身不去除首尾空白（与登录口径一致）。
 */
public final class PasswordPolicy {

    public static final int MIN = 8;
    public static final int MAX = 32;

    private PasswordPolicy() {
    }

    /** 校验并原样返回密码；不合法抛出业务异常，密码不出现在异常信息中。 */
    public static String validate(String password) {
        if (password == null || password.length() < MIN || password.length() > MAX) {
            throw new BusinessException("密码长度必须为 " + MIN + "—32 个字符");
        }
        int classes = 0;
        if (password.chars().anyMatch(Character::isLetter)) {
            classes++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            classes++;
        }
        if (classes < 2) {
            throw new BusinessException("密码必须包含字母、数字、符号中的至少两类");
        }
        return password;
    }
}
