package com.fabriclab.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

/**
 * 后台界面语言：默认简体中文，顶栏「中文 / EN」通过 ?lang= 改写会话语言。
 * 与用户端同名参数、各自会话，互不干扰；业务异常提示按 LocaleContextHolder 解析，
 * 保证英文界面下的提示也是英文。
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    public static final String LANG_PARAM = "lang";

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(LANG_PARAM);
        registry.addInterceptor(interceptor);
    }
}
