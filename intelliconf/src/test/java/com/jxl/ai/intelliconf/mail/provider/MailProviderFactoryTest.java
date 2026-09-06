package com.jxl.ai.intelliconf.mail.provider;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailProviderFactoryTest {

    @Test
    void getProviderReturnsSmtpProvider() {
        SmtpMailProvider smtp = new SmtpMailProvider();
        TencentExmailProvider exmail = new TencentExmailProvider();
        MailProviderFactory factory = new MailProviderFactory(List.of(smtp, exmail));

        assertEquals(smtp, factory.getProvider(MailProviderType.SMTP));
    }

    @Test
    void getProviderReturnsTencentExmailProvider() {
        SmtpMailProvider smtp = new SmtpMailProvider();
        TencentExmailProvider exmail = new TencentExmailProvider();
        MailProviderFactory factory = new MailProviderFactory(List.of(smtp, exmail));

        assertEquals(exmail, factory.getProvider(MailProviderType.TENCENT_EXMAIL));
    }

    @Test
    void unsupportedProviderTypeThrowsClearError() {
        MailProviderFactory factory = new MailProviderFactory(List.of());

        assertThrows(ClientException.class, () -> factory.getProvider(MailProviderType.SMTP));
    }
}
