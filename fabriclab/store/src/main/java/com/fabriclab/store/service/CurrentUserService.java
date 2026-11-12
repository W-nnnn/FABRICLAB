package com.fabriclab.store.service;

import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.model.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * 会话身份读取与数据归属校验（FR-U-02）。
 * 归属不符统一按 404「内容不存在」呈现，不泄露他人数据的存在性。
 */
@Service
public class CurrentUserService {

    /** 会话属性键：用户端独立命名空间，与管理端（8081）互不影响。 */
    public static final String KEY_USER_ID = "storeUserId";
    public static final String KEY_USERNAME = "storeUsername";
    public static final String KEY_DISPLAY_NAME = "storeDisplayName";

    /** 从会话读取当前登录身份；未登录返回 null。 */
    public CurrentUser from(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object id = session.getAttribute(KEY_USER_ID);
        Object username = session.getAttribute(KEY_USERNAME);
        Object displayName = session.getAttribute(KEY_DISPLAY_NAME);
        if (id instanceof Long userId && username instanceof String name) {
            return new CurrentUser(userId, name, displayName instanceof String d ? d : name);
        }
        return null;
    }

    /** 数据归属校验：会话用户必须与资源属主一致，否则 404。 */
    public void requireOwner(CurrentUser current, Long resourceOwnerId) {
        if (current == null || resourceOwnerId == null || !current.id().equals(resourceOwnerId)) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
    }
}
