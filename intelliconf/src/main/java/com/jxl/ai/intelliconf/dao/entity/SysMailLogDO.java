package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 邮件发送明细日志
 */
@TableName("sys_mail_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysMailLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务日志 ID
     */
    private Long taskLogId;

    /**
     * 收件箱邮箱
     */
    private String recipientEmail;

    /**
     * 发送状态：1=成功,0=失败
     */
    private Integer sendStatus;

    /**
     * 失败原因
     */
    private String errorMsg;

    private Date createTime;
}
