package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ConferenceTaskRespDTO {
    private Long taskId;
    private Long taskInstanceId;
    private Long conferenceId;
    private Long stageId;
    private Long milestoneId;
    private String stageCode;
    private String nodeCode;
    private String taskCode;
    private String taskName;
    private String taskDesc;
    private String taskType;
    private String completionType;
    private String principalRole;
    private Long principalUserId;
    private String principalName;
    private String priority;
    private String riskLevel;
    private Integer isCore;
    private Integer needReview;
    private Integer sortOrder;
    private String completionDesc;
    private String completionUrl;
    private Long completedBy;
    private String completedByName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submittedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedEndTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndTime;
    private String taskStatus;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dueTime;
    private String jumpUrl;
    private String currentHandlerId;
    private String currentHandlerName;
    private String assigneeType;
    private String assigneeUserId;
    private String assigneeUserName;
    private String assigneeRoleCode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
