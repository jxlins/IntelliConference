package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_attachment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskAttachmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long taskId;

    private String fileName;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private Long uploadedBy;

    private String uploadedByName;

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
