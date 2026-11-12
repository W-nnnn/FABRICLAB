package com.fabriclab.admin.config;

import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.security.AdminAuthInterceptor;
import com.fabriclab.admin.security.CsrfInterceptor;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminSessionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 拦截器装配：全部 POST 校验 CSRF 令牌；后台路径统一做访问检查。
 * 检查范围为 /admin/**，登录页例外——未登录与角色不符都不得进入后台页面与操作。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfService csrfService;
    private final StoreUserMapper storeUserMapper;
    private final AdminSessionService adminSessionService;

    public WebMvcConfig(CsrfService csrfService, StoreUserMapper storeUserMapper,
                        AdminSessionService adminSessionService) {
        this.csrfService = csrfService;
        this.storeUserMapper = storeUserMapper;
        this.adminSessionService = adminSessionService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfInterceptor(csrfService));
        registry.addInterceptor(new AdminAuthInterceptor(storeUserMapper, adminSessionService))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/signon");
    }
}
