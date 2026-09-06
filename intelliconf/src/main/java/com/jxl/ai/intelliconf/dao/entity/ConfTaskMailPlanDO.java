package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conf_task_mail_plan")
public class ConfTaskMailPlanDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conferenceId;
    private Long taskId;
    private String targetRole;
    private String subject;
    private String contentBody;
    private Date plannedSendTime;
    private String status;
    private Integer recipientCount;
    private Integer successCount;
    private Integer failCount;
    private String generatedBy;
    private Date generatedAt;
    private String approvedBy;
    private Date approvedAt;
    private Date sentAt;
    private String errorMessage;
    /** 审核时勾选的收件人邮箱，换行分隔；为空表示发送给全部角色收件人 */
    private String selectedEmails;
    private Date createdAt;
    private Date updatedAt;
}
