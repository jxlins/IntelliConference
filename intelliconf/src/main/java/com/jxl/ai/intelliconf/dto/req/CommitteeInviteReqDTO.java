package com.jxl.ai.intelliconf.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class CommitteeInviteReqDTO {

    private String email;

    private String name;

    /**
     * CHAIR / REVIEWER
     */
    private String role;

    /**
     * 不传则默认 7 天
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date tokenExpireTime;

    /**
     * 邮件模板ID
     */
    private Long templateId;
}
