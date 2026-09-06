package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskAssignmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskInstanceId;

    private Long conferenceId;

    private String assigneeType;

    private String assigneeUserId;

    private String assigneeUserName;

    private String assigneeRoleCode;

    private String status;

    private String assignedBy;

    private Date assignedAt;

    private Date acceptedAt;

    private Date completedAt;

    private Date createdAt;

    private Date updatedAt;
}
