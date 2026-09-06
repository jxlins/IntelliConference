package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会议成员关联持久层实体
 */
@TableName("conf_member")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfMemberDO {

    private Long id;

    /**
     * 具体会议ID
     */
    private Long confId;

    /**
     * 关联 conf_contact_pool 中的 ID
     */
    private Long contactId;

    /**
     * 在此会议中的身份（如 AUTHOR, REVIEWER）
     */
    private String role;

    private Date createTime;
}
