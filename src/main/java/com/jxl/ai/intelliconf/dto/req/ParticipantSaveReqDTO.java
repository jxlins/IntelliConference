package com.jxl.ai.intelliconf.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 添加/修改参会者请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantSaveReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会议ID（添加时必填）
     */
    private Long confId;

    /**
     * 姓名（必填）
     */
    private String name;

    /**
     * 邮箱（必填）
     */
    private String email;

    /**
     * 所在单位（可选）
     */
    private String institution;

    /**
     * 角色（可选，默认 PROSPECT）
     */
    private String role;
}
