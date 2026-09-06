package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("mail_template")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conferenceId;

    private String sceneCode;

    private String templateName;

    private String subjectTemplate;

    private String htmlTemplate;

    private String textTemplate;

    private Boolean enabled;

    private Date createdAt;

    private Date updatedAt;
}
