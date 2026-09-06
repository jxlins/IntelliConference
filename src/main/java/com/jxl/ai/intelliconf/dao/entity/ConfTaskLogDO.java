package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_task_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfTaskLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long taskId;

    private String operationType;

    private String oldValue;

    private String newValue;

    private Long operatorId;

    private String operatorName;

    private Date operationTime;

    private String remark;
}
