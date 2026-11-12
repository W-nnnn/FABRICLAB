package com.fabriclab.store.service;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.model.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录（FR-U-01）：仅启用的 CUSTOMER 可登录用户端。
 * 统一失败提示覆盖不存在、错误密码、禁用与角色不符，不枚举具体原因；
 * 密码不去空格、不回显、不记录日志；成功后更换会话标识（会话固定防护），
 * 只保存最小身份；不提供记住登录、找回密码、第三方登录。
 */
@Service
public class AuthService {

    /** 统一失败提示：四类失败共用，避免账号枚举。 */
    public static final String UNIFIED_FAILURE = "账号或密码错误，或账号不可用";

    private final StoreUserMapper storeUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(StoreUserMapper storeUserMapper) {
        this.storeUserMapper = storeUserMapper;
    }

    public CurrentUser login(String rawUsername, String rawPassword, HttpServletRequest request) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isEmpty() || username.length() > 50 || rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException(UNIFIED_FAILURE);
        }
        StoreUser user = storeUserMapper.selectOne(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getUsername, username).last("LIMIT 1"));
        boolean passwordOk = user != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        boolean usable = user != null
                && "CUSTOMER".equals(user.getRole())
                && "ENABLED".equals(user.getStatus());
        if (!passwordOk || !usable) {
            throw new BusinessException(UNIFIED_FAILURE);
        }
        // 会话固定防护：校验通过后更换会话标识，再写入身份。
        request.changeSessionId();
        var session = request.getSession();
        session.setAttribute(CurrentUserService.KEY_USER_ID, user.getId());
        session.setAttribute(CurrentUserService.KEY_USERNAME, user.getUsername());
        session.setAttribute(CurrentUserService.KEY_DISPLAY_NAME,
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        return new CurrentUser(user.getId(), user.getUsername(),
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
    }

    /** 站内返回页白名单：仅接受本应用相对路径，否则回落首页。 */
    public String safeReturnPath(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/main";
        }
        String path = returnTo.trim();
        if (path.startsWith("/") && !path.startsWith("//") && !path.contains(":") && !path.contains("\\")) {
            return path;
        }
        return "/main";
    }
}
