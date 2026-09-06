package com.jxl.ai.intelliconf.mail.provider;

public interface MailProvider {

    MailProviderType type();

    MailSendResult send(MailAccountConfig config, MailMessage message);

    MailCheckResult checkConnection(MailAccountConfig config);
}
