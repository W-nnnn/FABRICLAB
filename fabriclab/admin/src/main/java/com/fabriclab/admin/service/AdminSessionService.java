package com.fabriclab.admin.service;

import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.model.AdminUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 后台会话身份读写。会话属性键使用后台独立命名空间（admin 前缀），
 * 与用户端（store 前缀）互不影响——两端即使同名 Cookie 也不会串用身份。
 */
@Service
public class AdminSessionService {

    public static final String KEY_ADMIN_ID = "adminUserId";
    public static final String KEY_ADMIN_USERNAME = "adminUsername";
    public static final String KEY_ADMIN_DISPLAY_NAME = "adminDisplayName";
    public static final String KEY_SIGNIN_AT = "adminSigninAt";

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 从会话读取当前登录管理员身份；未登录返回 null。 */
    public AdminUser from(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object id = session.getAttribute(KEY_ADMIN_ID);
        Object username = session.getAttribute(KEY_ADMIN_USERNAME);
        Object displayName = session.getAttribute(KEY_ADMIN_DISPLAY_NAME);
        if (id instanceof Long userId && username instanceof String name) {
            return new AdminUser(userId, name, displayName instanceof String d ? d : name);
        }
        return null;
    }

    /** 本次登录时间（展示用）；未登录返回 null。 */
    public String signinAt(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(KEY_SIGNIN_AT);
        return value instanceof String text ? text : null;
    }

    /** 写入最小身份与登录时间；调用方须先完成会话标识更换（会话固定防护）。 */
    public AdminUser store(HttpSession session, StoreUser user, LocalDateTime when) {
        String displayName = user.getDisplayName() == null ? user.getUsername() : user.getDisplayName();
        session.setAttribute(KEY_ADMIN_ID, user.getId());
        session.setAttribute(KEY_ADMIN_USERNAME, user.getUsername());
        session.setAttribute(KEY_ADMIN_DISPLAY_NAME, displayName);
        session.setAttribute(KEY_SIGNIN_AT, when.format(DISPLAY));
        return new AdminUser(user.getId(), user.getUsername(), displayName);
    }
}
