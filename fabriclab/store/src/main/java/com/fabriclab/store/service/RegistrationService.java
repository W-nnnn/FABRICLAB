package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.PasswordPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 会员注册（指导书「注册 / 验证码」兼容实现）：
 * 仅创建 CUSTOMER / ENABLED 账号；用户名不区分大小写唯一；
 * 密码 8—32 且至少两类字符，BCrypt 散列存储，明文不落库不回显不记日志；
 * 注册成功后由用户自行登录，不做自动登录，不提供找回密码与第三方登录。
 */
@Service
public class RegistrationService {

    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String STATUS_ENABLED = "ENABLED";

    private final StoreUserMapper storeUserMapper;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RegistrationService(StoreUserMapper storeUserMapper, CaptchaService captchaService) {
        this.storeUserMapper = storeUserMapper;
        this.captchaService = captchaService;
    }

    /** 注册并返回已创建账号；任何一步不合法抛出业务异常，密码不出现在任何提示中。 */
    public StoreUser register(String username, String password, String confirm,
                              String captchaToken, String captchaInput) {
        String name = normalizeUsername(username);
        PasswordPolicy.validate(password);
        if (!password.equals(confirm)) {
            throw new BusinessException("两次输入的密码不一致");
        }
        captchaService.verify(captchaToken, captchaInput);
        requireUsernameAvailable(name);

        StoreUser user = new StoreUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(name);
        user.setRole(ROLE_CUSTOMER);
        user.setStatus(STATUS_ENABLED);
        try {
            storeUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户名已被使用");
        }
        return user;
    }

    /** 用户名必填、去首尾空白、最长 50 字符（与登录口径一致）。 */
    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        String name = username.trim();
        if (name.length() > 50) {
            throw new BusinessException("用户名最长 50 个字符");
        }
        return name;
    }

    /** 唯一性由唯一索引与查询共同保证；列排序规则不区分大小写。 */
    private void requireUsernameAvailable(String name) {
        Long count = storeUserMapper.selectCount(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getUsername, name));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已被使用");
        }
    }
}
