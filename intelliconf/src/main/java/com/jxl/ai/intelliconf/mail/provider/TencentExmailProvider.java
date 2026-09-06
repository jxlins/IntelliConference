package com.jxl.ai.intelliconf.mail.provider;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TencentExmailProvider extends SmtpMailProvider {

    public static final String HOST = "smtp.exmail.qq.com";
    public static final int PORT = 465;

    @Override
    public MailProviderType type() {
        return MailProviderType.TENCENT_EXMAIL;
    }

    @Override
    public MailSendResult send(MailAccountConfig config, MailMessage message) {
        return super.send(normalize(config), message);
    }

    @Override
    public MailCheckResult checkConnection(MailAccountConfig config) {
        return super.checkConnection(normalize(config));
    }

    public MailAccountConfig normalize(MailAccountConfig config) {
        MailAccountConfig normalized = copyConfig(config);
        String email = StringUtils.hasText(normalized.getFromEmail())
                ? normalized.getFromEmail().trim()
                : normalized.getUsername();
        normalized.setProviderType(MailProviderType.TENCENT_EXMAIL);
        normalized.setSmtpHost(HOST);
        normalized.setSmtpPort(PORT);
        normalized.setSslEnabled(true);
        normalized.setStarttlsEnabled(false);
        normalized.setUsername(email);
        normalized.setFromEmail(email);
        return normalized;
    }
}
