package com.fabriclab.admin.model;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 列表页大小记忆：三个列表共用同一个 cookie，默认值仍按各自文档（商品 20、订单与账号 10），
 * 但只要用户在任何一处改过，其余列表就沿用同一个选择，不再出现两套口径。
 */
public final class PageSize {

    public static final String COOKIE = "fl_psize";
    private static final int[] ALLOWED = {10, 20, 50};

    private PageSize() {
    }

    /** 优先取请求参数，其次取上次选择；都没有返回 null，由服务层用各自的缺省值。 */
    public static Integer resolve(HttpServletRequest request) {
        String raw = request.getParameter("size");
        Integer fromParam = valid(raw);
        if (fromParam != null) return fromParam;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE.equals(c.getName())) {
                    Integer fromCookie = valid(c.getValue());
                    if (fromCookie != null) return fromCookie;
                }
            }
        }
        return null;
    }

    /** 用户这次明确选了页大小就记住，供其他列表沿用。 */
    public static void remember(HttpServletResponse response, HttpServletRequest request) {
        Integer chosen = valid(request.getParameter("size"));
        if (chosen == null) return;
        Cookie cookie = new Cookie(COOKIE, String.valueOf(chosen));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(60 * 60 * 24 * 180);
        response.addCookie(cookie);
    }

    private static Integer valid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            for (int n : ALLOWED) {
                if (n == v) return v;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
