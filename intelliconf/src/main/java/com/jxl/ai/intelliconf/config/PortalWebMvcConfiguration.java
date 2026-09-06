package com.jxl.ai.intelliconf.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PortalWebMvcConfiguration implements WebMvcConfigurer {

    private final PortalSecurityInterceptor portalSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(portalSecurityInterceptor)
                .addPathPatterns("/api/portal/**");
    }
}
