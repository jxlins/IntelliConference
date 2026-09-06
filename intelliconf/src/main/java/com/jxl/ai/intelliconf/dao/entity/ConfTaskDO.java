package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long stageId;

    private Long taskDefId;

    private String stageCode;

    private String taskCode;

    private String taskName;

    private String taskDesc;

    private String taskType;

    private String principalRole;

    private Long principalUserId;

    private String principalName;

    private Date plannedStartTime;

    private Date plannedEndTime;

    private Date actualStartTime;

    private Date actualEndTime;

    private String taskStatus;

    private String priority;

    private String riskLevel;

    private Integer isCore;

    private String completionType;

    private String completionDesc;

    private String completionUrl;

    private Long completedBy;

    private String completedByName;

    private Date submittedAt;

    private Long reviewedBy;

    private String reviewedByName;

    private Date reviewedAt;

    private String reviewComment;

    private Integer needReview;

    private String outputDesc;

    private Integer sortOrder;

    private String remark;

    private Long createUser;

    private Long updateUser;

    private Date createTime;

    private Date updateTime;
}
