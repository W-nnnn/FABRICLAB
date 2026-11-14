package com.fabriclab.store.service;

import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.model.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    /**
     * 登录时口令散列的指纹。后台重置口令会整条替换散列，指纹随之对不上，
     * 受保护请求复核时即判定为「凭证已变」并失效会话（UC-A7）。
     */
    public static final String KEY_CREDENTIAL_TAG = "storeCredentialTag";

    /** 散列的散列：取 SHA-256 前 16 位十六进制，仅用于比对，不可还原为口令。 */
    public static String credentialTag(String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

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
