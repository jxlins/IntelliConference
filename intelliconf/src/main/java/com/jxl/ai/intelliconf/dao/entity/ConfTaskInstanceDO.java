package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskInstanceDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long milestoneId;

    private Long taskDefId;

    private String nodeCode;

    private String taskCode;

    private String taskName;

    private String taskDesc;

    private String taskType;

    private String handlerBean;

    private String params;

    private String priority;

    private String status;

    private Date dueTime;

    private String jumpUrl;

    private String currentHandlerId;

    private String currentHandlerName;

    private String createdBy;

    private Date createdAt;

    private Date updatedAt;
}
