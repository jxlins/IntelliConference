package com.jxl.ai.intelliconf.config;

import com.alibaba.fastjson2.JSON;
import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteeContext;
import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteePrincipal;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.service.PortalAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Date;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PortalSecurityInterceptor implements HandlerInterceptor {

    private final PortalAccessService portalAccessService;

    public String generateToken(String email, String role) {
        return portalAccessService.generateToken(email, role);
    }

    public String generateToken(Long confId, String email, String name, String role, Date tokenExpireTime) {
        return portalAccessService.generateToken(confId, email, name, role, tokenExpireTime);
    }

    public PortalCommitteePrincipal validateToken(String token) {
        return portalAccessService.validateToken(token);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = readToken(request);
        PortalCommitteePrincipal principal = validateToken(token);
        if (principal == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(Results.failure("PORTAL_TOKEN_INVALID", "门户令牌无效或已过期")));
            return false;
        }

        PortalCommitteeContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        PortalCommitteeContext.clear();
    }

    private String readToken(HttpServletRequest request) {
        String token = request.getHeader("X-Portal-Token");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("Portal-Token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("accessToken");
        }
        return token;
    }
}
