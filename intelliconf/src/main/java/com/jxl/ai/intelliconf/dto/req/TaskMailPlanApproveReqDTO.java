package com.jxl.ai.intelliconf.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class TaskMailPlanApproveReqDTO {
    private String subject;
    private String contentBody;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedSendTime;
    /** 审核时勾选的收件人邮箱；为空或 null 表示发送给该角色的全部收件人 */
    private List<String> selectedEmails;
}
