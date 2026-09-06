package com.jxl.ai.intelliconf.common.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jxl.ai.intelliconf.common.convention.errorcode.BaseErrorCode;
import com.jxl.ai.intelliconf.common.convention.exception.AbstractException;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器
 */
@Slf4j
@Component
@RestControllerAdvice   // 统一处理整个应用程序中控制器（@RestController 或 @Controller）抛出的异常，并以 JSON 格式返回结构化错误响应。
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @SneakyThrows   // Lombok 注解，静默抛出受检异常，无需 try-catch 或 throws 声明
    @ExceptionHandler(value = MethodArgumentNotValidException.class)  // 表示该方法专门处理 MethodArgumentNotValidException 异常
    // 参数验证异常：某个实例定义了校验规则（@NotBlank(message = "用户名不能为空")），而客户端发送非法数据时会直接抛出 MethodArgumentNotValidException 异常
    public Result validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {

        BindingResult bindingResult = ex.getBindingResult();    // 获取校验结果，BindingResult 包含了所有字段的校验错误（FieldError 列表）
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());   // 取第一个错误

        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);

        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截应用内抛出的异常
     * @param request
     * @param ex
     * @return
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {    // 获取当前异常的根本原因

            // 日志格式：[POST] /api/short-link/create [ex] com.xxx.AbstractException: 用户不存在
            // 最后一个参数如果是 Throwable，会被特殊处理：
            // ex.getCause()不会被当作普通参数填充到 {} 中，而是被当作“异常原因”单独处理——即自动打印完整的堆栈跟踪（stack trace）
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURI().toString(), ex.toString(), ex.getCause());
            return Results.failure(ex);
        }

        log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURI().toString(), ex.toString());
        return Results.failure(ex);
    }

    /**
     * 拦截未捕获异常
     * @param request
     * @param throwable
     * @return
     */
    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    /**
     * 获取当前 HTTP 请求的完整 URL（包含查询参数 query string）。
     * @param request
     * @return /api/user（无参数）  /api/user?id=123&name=Alice（带参数）
     */
    private String getUrl(HttpServletRequest request) {
        if (StringUtils.isEmpty(request.getQueryString())) {
            return request.getRequestURI().toString();
        }
        return request.getRequestURI().toString() + "?" + request.getQueryString();
    }
}