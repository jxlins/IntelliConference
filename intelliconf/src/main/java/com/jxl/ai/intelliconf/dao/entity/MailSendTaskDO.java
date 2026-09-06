package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("mail_send_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private Long templateId;

    private String subject;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private String createdBy;

    private Date createdAt;

    private Date updatedAt;
}
