package com.fabriclab.admin.config;

import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.security.AdminAuthInterceptor;
import com.fabriclab.admin.security.CsrfInterceptor;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.ProductImageStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 拦截器装配：全部 POST 校验 CSRF 令牌；后台路径统一做访问检查。
 * 检查范围为 /admin/**，登录页例外——未登录与角色不符都不得进入后台页面与操作。
 * 商品图片同时挂上传目录与打包静态目录：新上传的图不必重启即可访问，既有图照旧可用。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfService csrfService;
    private final StoreUserMapper storeUserMapper;
    private final AdminSessionService adminSessionService;
    private final ObjectProvider<ProductImageStorage> imageStorage;

    public WebMvcConfig(CsrfService csrfService, StoreUserMapper storeUserMapper,
                        AdminSessionService adminSessionService,
                        ObjectProvider<ProductImageStorage> imageStorage) {
        this.csrfService = csrfService;
        this.storeUserMapper = storeUserMapper;
        this.adminSessionService = adminSessionService;
        this.imageStorage = imageStorage;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfInterceptor(csrfService));
        registry.addInterceptor(new AdminAuthInterceptor(storeUserMapper, adminSessionService))
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/signon");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        ProductImageStorage storage = imageStorage.getIfAvailable();
        if (storage == null) {
            registry.addResourceHandler("/images/products/**")
                    .addResourceLocations("classpath:/static/images/products/");
            return;
        }
        String uploaded = storage.root().resolve("images/products").toUri().toString();
        if (!uploaded.endsWith("/")) {
            uploaded = uploaded + "/";
        }
        registry.addResourceHandler("/images/products/**")
                .addResourceLocations(uploaded, "classpath:/static/images/products/");
    }
}
