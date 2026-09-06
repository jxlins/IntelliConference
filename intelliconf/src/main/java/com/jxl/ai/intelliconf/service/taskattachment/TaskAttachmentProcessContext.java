package com.jxl.ai.intelliconf.service.taskattachment;

import com.jxl.ai.intelliconf.dao.entity.ConfTaskAttachmentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import lombok.Builder;
import lombok.Value;
import org.springframework.web.multipart.MultipartFile;

@Value
@Builder
public class TaskAttachmentProcessContext {

    Long conferenceId;

    ConfTaskDO task;

    ConfTaskAttachmentDO attachment;

    MultipartFile file;

    Long operatorId;

    String operatorName;
}
