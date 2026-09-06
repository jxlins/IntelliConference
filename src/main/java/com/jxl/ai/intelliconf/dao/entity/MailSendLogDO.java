package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("mail_send_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long conferenceId;

    private String recipientEmail;

    private String subject;

    private String status;

    private String errorCode;

    private String errorMessage;

    private String providerType;

    private Date sentAt;

    private Date createdAt;
}
