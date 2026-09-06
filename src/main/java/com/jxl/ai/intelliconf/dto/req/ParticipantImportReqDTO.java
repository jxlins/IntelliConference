package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 参会者导入请求 DTO
 */
@Data
public class ParticipantImportReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会议ID
     */
    private Long confId;

    /**
     * 角色（默认为 PROSPECT）
     */
    private String role = "PROSPECT";
}
