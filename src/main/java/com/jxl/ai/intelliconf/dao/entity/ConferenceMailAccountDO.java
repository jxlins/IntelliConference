package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conference_mail_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConferenceMailAccountDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private String providerType;

    private String fromEmail;

    private String fromName;

    private String replyTo;

    private String smtpHost;

    private Integer smtpPort;

    private String username;

    private String passwordCipher;

    private Boolean sslEnabled;

    private Boolean starttlsEnabled;

    private Boolean enabled;

    private String lastTestStatus;

    private Date lastTestTime;

    private Date createdAt;

    private Date updatedAt;
}
