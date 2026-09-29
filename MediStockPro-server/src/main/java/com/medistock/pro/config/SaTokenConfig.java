package com.medistock.pro.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.medistock.pro.modules.system.OperationLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置: 登录校验拦截器 + 操作日志拦截器
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // OPTIONS 预检按规范不携带凭证, 放行给 CORS 机制应答, 避免 NotLoginException 刷堆栈
                    if (HttpMethod.OPTIONS.matches(SaHolder.getRequest().getMethod())) {
                        return;
                    }
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/doc.html", "/v3/api-docs/**", "/webjars/**", "/favicon.ico",
                        "/error"
                );
        // 操作日志: 在登录校验之后执行, 仅记录写操作
        registry.addInterceptor(operationLogInterceptor).addPathPatterns("/api/**");
    }
}
