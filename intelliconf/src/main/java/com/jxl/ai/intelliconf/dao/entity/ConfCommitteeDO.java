package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jxl.ai.intelliconf.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_committee")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfCommitteeDO extends BaseDO {

    private Long id;

    private Long confId;

    private String email;

    private String name;

    private String institution;

    /**
     * 委员会角色：CHAIR / REVIEWER
     */
    private String role;

    /**
     * 邀请状态：INVITED / ACCEPTED / DECLINED
     */
    private String inviteStatus;

    /**
     * 门户访问令牌
     */
    private String accessToken;

    /**
     * 令牌过期时间
     */
    private Date tokenExpireTime;
}
