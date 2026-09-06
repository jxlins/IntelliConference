package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
public class TaskMailPlanRespDTO {
    private Long planId;
    private Long conferenceId;
    private Long taskId;
    private String taskCode;
    private String taskName;
    private String stageCode;
    private String principalRole;
    private Long principalUserId;
    private String principalName;
    private String targetRole;
    private String subject;
    private String contentBody;
    /** 供审核页展示的纯文本正文（自动去除 HTML 标签、转换换行） */
    private String plainBody;
    private String status;
    private Integer recipientCount;
    private Integer successCount;
    private Integer failCount;
    private List<ContactDTO> recipients;
    /** 审核时勾选的收件人邮箱；为空表示全部收件人 */
    private List<String> selectedEmails;
    private String errorMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedSendTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date generatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date approvedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sentAt;
}
