package com.fabriclab.store.config;

import com.fabriclab.store.security.AuthInterceptor;
import com.fabriclab.store.security.CsrfInterceptor;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.service.CurrentUserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 拦截器装配：全部 POST 校验 CSRF 令牌；受保护入口做身份、
 * 角色与启用状态检查（数据归属在业务入口用 CurrentUserService.requireOwner 校验）。
 * 受保护路径为购物车 / 结算 / 订单 / 支付 / 账户前缀，对应模块在后续任务落地。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfService csrfService;
    private final StoreUserMapper storeUserMapper;
    private final CurrentUserService currentUserService;

    public WebMvcConfig(CsrfService csrfService, StoreUserMapper storeUserMapper,
                        CurrentUserService currentUserService) {
        this.csrfService = csrfService;
        this.storeUserMapper = storeUserMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfInterceptor(csrfService));
        registry.addInterceptor(new AuthInterceptor(storeUserMapper, currentUserService))
                .addPathPatterns("/cart/**", "/checkout/**", "/orders/**", "/payment/**", "/account/**");
    }
}
