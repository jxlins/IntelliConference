package com.jxl.ai.intelliconf.mail.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailMessage {

    private List<String> to;

    private List<String> cc;

    private List<String> bcc;

    private String subject;

    private String htmlBody;

    private String textBody;
}
