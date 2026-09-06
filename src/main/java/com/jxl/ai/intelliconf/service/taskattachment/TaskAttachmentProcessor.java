package com.jxl.ai.intelliconf.service.taskattachment;

public interface TaskAttachmentProcessor {

    boolean supports(String taskCode);

    TaskAttachmentProcessResult process(TaskAttachmentProcessContext context);
}
