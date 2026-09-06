package com.jxl.ai.intelliconf.mail.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendResult {

    private boolean success;

    private MailErrorCode errorCode;

    private String errorMessage;

    public static MailSendResult ok() {
        return MailSendResult.builder().success(true).build();
    }

    public static MailSendResult fail(MailErrorCode errorCode, String errorMessage) {
        return MailSendResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
