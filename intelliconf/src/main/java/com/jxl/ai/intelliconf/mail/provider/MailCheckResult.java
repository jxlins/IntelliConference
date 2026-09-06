package com.jxl.ai.intelliconf.mail.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCheckResult {

    private boolean success;

    private MailErrorCode errorCode;

    private String errorMessage;

    public static MailCheckResult ok() {
        return MailCheckResult.builder().success(true).build();
    }

    public static MailCheckResult fail(MailErrorCode errorCode, String errorMessage) {
        return MailCheckResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
