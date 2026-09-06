package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_def")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfTaskDefDO {

    private Long id;

    private Long stageDefId;

    private String taskCode;

    private String taskName;

    private String taskDesc;

    private String taskType;

    private String defaultRole;

    private Integer isCore;

    private String completionType;

    private Integer sortOrder;

    private String offsetBase;

    private Integer startOffsetDays;

    private Integer endOffsetDays;

    private Integer needReview;

    private Integer status;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private String nodeCode;

    @TableField(exist = false)
    private String handlerBean;

    @TableField(exist = false)
    private String params;

    @TableField(exist = false)
    private String defaultAssigneeType;

    @TableField(exist = false)
    private String defaultAssigneeRole;

    @TableField(exist = false)
    private String deadlineRule;

    @TableField(exist = false)
    private String jumpUrl;

    @TableField(exist = false)
    private Integer isAsync;

    @TableField(exist = false)
    private Integer priority;

    @TableField(exist = false)
    private Date createdAt;

    @TableField(exist = false)
    private Date updatedAt;
}
