package com.jxl.ai.intelliconf.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jxl.ai.intelliconf.common.constant.RedisCacheConstant.USER_LOGIN_KEY;

/**
 * 用户信息传输过滤器
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    private static final List<String> IGNORE_URI = Lists.newArrayList(
            "/api/intelli-conf/v1/user/login",
            "/api/intelli-conf/v1/user/has-username"
    );

    private final StringRedisTemplate stringRedisTemplate;

    @SneakyThrows
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String requestURI = httpServletRequest.getRequestURI();
        if (requestURI != null && requestURI.startsWith("/api/portal/")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (requestURI != null && requestURI.startsWith("/api/committee-invitations/token/")) {
            String method = httpServletRequest.getMethod();
            if (Objects.equals(method, "GET") || requestURI.endsWith("/decline")) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
        }
        if (!IGNORE_URI.contains(requestURI)) {
            String method = httpServletRequest.getMethod();
            if (!(Objects.equals(requestURI, "/api/intelli-conf/v1/user") && Objects.equals(method, "POST"))) {
                String username = httpServletRequest.getHeader("username");
                String token = httpServletRequest.getHeader("token");
                if (!StrUtil.isAllNotBlank(username, token)) {
                    returnJson((HttpServletResponse) servletResponse, JSON.toJSONString(Results.failure(new ClientException("用户 Token 错误"))));
                    return;
                }
                Object userInfoJsonStr;
                try {
                    userInfoJsonStr = stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token);
                    if (userInfoJsonStr == null) {
                        throw new ClientException("用户 Token 错误");
                    }
                } catch (Exception ex) {
                    returnJson((HttpServletResponse) servletResponse, JSON.toJSONString(Results.failure(new ClientException("用户 Token 错误"))));
                    return;
                }
                UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
                
                // Token 刷新逻辑：每次验证成功后，刷新 Redis 中 token 的过期时间为 30 分钟
                // 这样用户只要在使用系统，token 就会持续有效，长时间不操作才会过期
                try {
                    String redisKey = USER_LOGIN_KEY + username;
                    stringRedisTemplate.expire(redisKey, 30L, TimeUnit.MINUTES);
                } catch (Exception ex) {
                    // 刷新失败不影响本次请求，只记录日志
                    System.err.println("Token 刷新失败: " + ex.getMessage());
                }
            }
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.removeUser();
        }
    }

    private void returnJson(HttpServletResponse response, String json) throws Exception {
        PrintWriter writer = null;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        try {
            writer = response.getWriter();
            writer.print(json);

        } catch (IOException e) {
        } finally {
            if (writer != null)
                writer.close();
        }
    }
}
