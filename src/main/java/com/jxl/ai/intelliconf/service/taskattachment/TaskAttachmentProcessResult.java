package com.jxl.ai.intelliconf.service.taskattachment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskAttachmentProcessResult {

    String processType;

    String processStatus;

    String processMessage;

    String processResult;

    Integer totalCount;

    Integer successCount;

    Integer failedCount;

    Integer duplicateCount;
}
