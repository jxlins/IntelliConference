package com.jxl.ai.intelliconf.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailSendTaskRespDTO {

    private Long taskId;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;
}
