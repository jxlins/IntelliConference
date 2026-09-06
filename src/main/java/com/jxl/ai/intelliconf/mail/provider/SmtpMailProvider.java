package com.jxl.ai.intelliconf.mail.provider;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Properties;

@Slf4j
@Component
public class SmtpMailProvider implements MailProvider {

    @Override
    public MailProviderType type() {
        return MailProviderType.SMTP;
    }

    @Override
    public MailSendResult send(MailAccountConfig config, MailMessage message) {
        try {
            validateMessage(message);
            JavaMailSenderImpl sender = buildSender(config);
            MimeMessage mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(buildFrom(config));
            if (StringUtils.hasText(config.getReplyTo())) {
                helper.setReplyTo(config.getReplyTo().trim());
            }
            setAddresses(helper, message);
            helper.setSubject(message.getSubject());
            if (StringUtils.hasText(message.getHtmlBody())) {
                helper.setText(message.getTextBody() == null ? "" : message.getTextBody(), message.getHtmlBody());
            } else {
                helper.setText(message.getTextBody() == null ? "" : message.getTextBody(), false);
            }
            sender.send(mimeMessage);
            return MailSendResult.ok();
        } catch (Exception ex) {
            MailErrorCode code = mapErrorCode(ex);
            log.warn("[MailProvider] send failed, provider={}, to={}, code={}, message={}",
                    type(), safeRecipients(message), code, sanitize(ex.getMessage()));
            return MailSendResult.fail(code, userMessage(code, ex));
        }
    }

    @Override
    public MailCheckResult checkConnection(MailAccountConfig config) {
        try {
            buildSender(config).testConnection();
            return MailCheckResult.ok();
        } catch (Exception ex) {
            MailErrorCode code = mapErrorCode(ex);
            log.warn("[MailProvider] connection check failed, provider={}, username={}, code={}, message={}",
                    type(), config == null ? null : config.getUsername(), code, sanitize(ex.getMessage()));
            return MailCheckResult.fail(code, userMessage(code, ex));
        }
    }

    protected JavaMailSenderImpl buildSender(MailAccountConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(required(config.getSmtpHost(), "SMTP host is required"));
        sender.setPort(config.getSmtpPort() == null ? 25 : config.getSmtpPort());
        sender.setUsername(required(config.getUsername(), "SMTP username is required"));
        sender.setPassword(required(config.getPassword(), "SMTP password is required"));
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        if (Boolean.TRUE.equals(config.getSslEnabled())) {
            properties.put("mail.smtp.ssl.enable", "true");
        }
        if (Boolean.TRUE.equals(config.getStarttlsEnabled())) {
            properties.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    protected MailErrorCode mapErrorCode(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof AuthenticationFailedException || cur instanceof MailAuthenticationException) {
                return MailErrorCode.AUTH_FAILED;
            }
            if (cur instanceof SocketTimeoutException) {
                return MailErrorCode.TIMEOUT;
            }
            if (cur instanceof SendFailedException) {
                return MailErrorCode.INVALID_RECIPIENT;
            }
            if (cur instanceof ConnectException) {
                return MailErrorCode.CONNECTION_FAILED;
            }
            if (cur instanceof MessagingException && StringUtils.hasText(cur.getMessage())
                    && cur.getMessage().toLowerCase().contains("invalid address")) {
                return MailErrorCode.INVALID_RECIPIENT;
            }
            cur = cur.getCause();
        }
        if (ex instanceof MailSendException) {
            return MailErrorCode.PROVIDER_REJECTED;
        }
        return MailErrorCode.UNKNOWN;
    }

    protected String userMessage(MailErrorCode code, Throwable ex) {
        return switch (code) {
            case AUTH_FAILED -> "邮箱认证失败，请检查账号和客户端专用密码";
            case CONNECTION_FAILED -> "无法连接邮箱服务，请检查服务器地址、端口或网络";
            case TIMEOUT -> "邮箱服务连接超时，请稍后重试";
            case INVALID_RECIPIENT -> "收件人邮箱地址无效";
            case PROVIDER_REJECTED -> "邮箱服务商拒绝发送，请检查发件限制或邮件内容";
            default -> StringUtils.hasText(ex.getMessage()) ? sanitize(ex.getMessage()) : "邮件发送失败";
        };
    }

    protected MailAccountConfig copyConfig(MailAccountConfig config) {
        return MailAccountConfig.builder()
                .conferenceId(config.getConferenceId())
                .providerType(config.getProviderType())
                .fromEmail(config.getFromEmail())
                .fromName(config.getFromName())
                .replyTo(config.getReplyTo())
                .smtpHost(config.getSmtpHost())
                .smtpPort(config.getSmtpPort())
                .username(config.getUsername())
                .password(config.getPassword())
                .sslEnabled(config.getSslEnabled())
                .starttlsEnabled(config.getStarttlsEnabled())
                .build();
    }

    private InternetAddress buildFrom(MailAccountConfig config) throws UnsupportedEncodingException, MessagingException {
        String fromEmail = StringUtils.hasText(config.getFromEmail()) ? config.getFromEmail().trim() : config.getUsername();
        String fromName = StringUtils.hasText(config.getFromName()) ? config.getFromName().trim() : null;
        return StringUtils.hasText(fromName) ? new InternetAddress(fromEmail, fromName, "UTF-8") : new InternetAddress(fromEmail);
    }

    private void setAddresses(MimeMessageHelper helper, MailMessage message) throws MessagingException {
        helper.setTo(toArray(message.getTo()));
        if (!CollectionUtils.isEmpty(message.getCc())) {
            helper.setCc(toArray(message.getCc()));
        }
        if (!CollectionUtils.isEmpty(message.getBcc())) {
            helper.setBcc(toArray(message.getBcc()));
        }
    }

    private String[] toArray(List<String> values) {
        return values.stream().filter(StringUtils::hasText).map(String::trim).toArray(String[]::new);
    }

    private void validateMessage(MailMessage message) {
        if (message == null || CollectionUtils.isEmpty(message.getTo())) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (!StringUtils.hasText(message.getSubject())) {
            throw new IllegalArgumentException("subject is required");
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String safeRecipients(MailMessage message) {
        return message == null || message.getTo() == null ? "" : String.join(",", message.getTo());
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
