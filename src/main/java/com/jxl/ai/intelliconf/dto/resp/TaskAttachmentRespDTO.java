package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class TaskAttachmentRespDTO {

    private Long attachmentId;

    private String fileName;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private Long uploadedBy;

    private String uploadedByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date uploadedAt;

    private String processStatus;

    private String processType;

    private Long relatedBatchId;

    private String processMessage;

    private String processResult;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer duplicateCount;
}
