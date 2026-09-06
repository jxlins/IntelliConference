package com.jxl.ai.intelliconf.toolkit;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;

/**
 * 邮件发送工具类
 * <p>
 * 提供同步发送和异步发送两种入口。
 */
@Slf4j
@RequiredArgsConstructor
@Deprecated
public class EmailUtil {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 同步发送 HTML 格式邮件
     *
     * @param to      收件人地址
     * @param subject 邮件主题
     * @param content HTML 正文内容
     */
    public void sendHtmlEmail(String to, String subject, String content) throws Exception {
        sendHtmlEmail(to, subject, content, null);
    }

    public void sendHtmlEmail(String to, String subject, String content, String senderEmail) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("[EmailUtil] 开始发送邮件, to={}, subject={}", to, subject);

        try {
            doSend(to, subject, content, senderEmail, senderEmail);
        } catch (Exception ex) {
            // 某些 SMTP 提供商不允许自定义 from，这里回退到系统账号并保留 reply-to。
            if (StringUtils.hasText(senderEmail) && !senderEmail.trim().equalsIgnoreCase(from)) {
                log.warn("[EmailUtil] 自定义发件地址发送失败，回退到系统发件账号, senderEmail={}, to={}", senderEmail, to);
                doSend(to, subject, content, from, senderEmail);
            } else {
                throw ex;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[EmailUtil] 邮件发送完成, to={}, subject={}, 耗时={}ms", to, subject, elapsed);
    }

    private void doSend(String to, String subject, String content, String fromAddress, String replyTo) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(StringUtils.hasText(fromAddress) ? fromAddress.trim() : from);
        if (StringUtils.hasText(replyTo)) {
            helper.setReplyTo(replyTo.trim());
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);

        mailSender.send(message);
    }

    /**
     * 异步发送 HTML 格式邮件（兼容旧调用）
     */
    @Async("emailTaskExecutor")
    public void sendAsyncHtmlEmail(String to, String subject, String content) {
        try {
            sendHtmlEmail(to, subject, content);
        } catch (Exception e) {
            log.error("[EmailUtil] 异步邮件发送失败, to={}, subject={}", to, subject, e);
        }
    }
}
