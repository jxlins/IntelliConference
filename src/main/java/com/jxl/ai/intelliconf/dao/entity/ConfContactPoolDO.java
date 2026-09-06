package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 联系人总库持久层实体
 */
@TableName("conf_contact_pool")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfContactPoolDO {

    private Long id;

    /**
     * 所属组织者ID
     */
    private Long ownerOrgId;

    /**
     * 姓名
     */
    private String name;

    /**
     * 邮箱（与 owner_org_id 组成唯一索引）
     */
    private String email;

    /**
     * 所在单位
     */
    private String institution;

    private Date createTime;
}
