package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 参会者信息响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * conf_member 表的 ID
     */
    private Long id;

    /**
     * 联系人ID
     */
    private Long contactId;

    /**
     * 姓名
     */
    private String name;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 所在单位
     */
    private String institution;

    /**
     * 在会议中的角色
     */
    private String role;

    /**
     * 加入时间
     */
    private Date createTime;
}
