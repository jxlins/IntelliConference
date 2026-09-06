package com.jxl.ai.intelliconf.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 修改参会者请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantUpdateReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * conf_member 表的 ID（必填）
     */
    private Long memberId;

    /**
     * 姓名（可选）
     */
    private String name;

    /**
     * 邮箱（可选）
     */
    private String email;

    /**
     * 所在单位（可选）
     */
    private String institution;

    /**
     * 角色（可选）
     */
    private String role;
}
