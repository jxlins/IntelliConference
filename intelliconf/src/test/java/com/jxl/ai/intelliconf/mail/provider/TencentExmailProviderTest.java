package com.jxl.ai.intelliconf.mail.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentExmailProviderTest {

    @Test
    void normalizeForcesTencentExmailSmtpConfig() {
        TencentExmailProvider provider = new TencentExmailProvider();
        MailAccountConfig config = MailAccountConfig.builder()
                .providerType(MailProviderType.TENCENT_EXMAIL)
                .fromEmail("conf@example.com")
                .smtpHost("evil.example.com")
                .smtpPort(25)
                .username("ignored")
                .password("secret")
                .sslEnabled(false)
                .starttlsEnabled(true)
                .build();

        MailAccountConfig normalized = provider.normalize(config);

        assertEquals(TencentExmailProvider.HOST, normalized.getSmtpHost());
        assertEquals(TencentExmailProvider.PORT, normalized.getSmtpPort());
        assertTrue(normalized.getSslEnabled());
        assertFalse(normalized.getStarttlsEnabled());
        assertEquals("conf@example.com", normalized.getUsername());
        assertEquals("conf@example.com", normalized.getFromEmail());
    }
}
