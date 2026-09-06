package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_action_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskActionLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskInstanceId;

    private Long conferenceId;

    private String operatorId;

    private String operatorName;

    private String operatorRoleCode;

    private String actionType;

    private String comment;

    private String beforeStatus;

    private String afterStatus;

    private Date createdAt;
}
