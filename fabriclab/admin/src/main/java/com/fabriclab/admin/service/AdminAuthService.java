package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 后台登录（FR-A-01）：仅启用的 ADMIN 可进入后台。
 * 不存在、口令错误、账号停用、角色不符四类失败共用统一提示，不枚举具体原因；
 * 口令不去空格、不回显、不记录日志；成功后更换会话标识（会话固定防护）再写最小身份。
 * 不提供记住登录、找回口令与第三方登录，也不提供管理员注册。
 */
@Service
public class AdminAuthService {

    /** 统一失败提示：四类失败共用，避免账号枚举。 */
    public static final String UNIFIED_FAILURE = "账号或口令不正确，或无后台权限";

    /** 后台内默认落地页。 */
    public static final String CONSOLE_PATH = "/admin/console";

    private final StoreUserMapper storeUserMapper;
    private final AdminSessionService adminSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(StoreUserMapper storeUserMapper, AdminSessionService adminSessionService) {
        this.storeUserMapper = storeUserMapper;
        this.adminSessionService = adminSessionService;
    }

    public AdminUser login(String rawUsername, String rawPassword, HttpServletRequest request) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isEmpty() || username.length() > 50 || rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException(UNIFIED_FAILURE);
        }
        StoreUser user = storeUserMapper.selectOne(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getUsername, username).last("LIMIT 1"));
        boolean passwordOk = user != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        boolean usable = user != null
                && "ADMIN".equals(user.getRole())
                && "ENABLED".equals(user.getStatus());
        if (!passwordOk || !usable) {
            throw new BusinessException(UNIFIED_FAILURE);
        }
        // 会话固定防护：校验通过后更换会话标识，再写入身份。
        request.changeSessionId();
        return adminSessionService.store(request.getSession(), user, LocalDateTime.now());
    }

    /** 站内返回页白名单：仅接受本后台 /admin 前缀的相对路径，否则回落控制台。 */
    public String safeReturnPath(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return CONSOLE_PATH;
        }
        String path = returnTo.trim();
        boolean inAdmin = path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/admin?");
        boolean plain = !path.startsWith("//") && !path.contains(":") && !path.contains("\\");
        return inAdmin && plain ? path : CONSOLE_PATH;
    }
}
