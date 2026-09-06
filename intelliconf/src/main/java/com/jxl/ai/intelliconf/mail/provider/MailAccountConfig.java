package com.jxl.ai.intelliconf.mail.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailAccountConfig {

    private Long conferenceId;

    private MailProviderType providerType;

    private String fromEmail;

    private String fromName;

    private String replyTo;

    private String smtpHost;

    private Integer smtpPort;

    private String username;

    /**
     * Runtime-only decrypted password. Never persist or return this value.
     */
    private String password;

    private Boolean sslEnabled;

    private Boolean starttlsEnabled;
}
