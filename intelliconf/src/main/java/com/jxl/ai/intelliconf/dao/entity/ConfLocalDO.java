package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jxl.ai.intelliconf.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议地点持久层实体
 */
@TableName("location")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfLocalDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 关联会议ID
     */
    private Long conferId;

    /**
     * 省名称
     */
    private String province;

    /**
     * 市名称
     */
    private String city;

    /**
     * 国家标识
     */
    private String country;

    /**
     * 详细地址
     */
    private String address;
}
