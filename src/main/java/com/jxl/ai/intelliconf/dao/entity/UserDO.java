package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jxl.ai.intelliconf.common.annotation.EncryptField;
import com.jxl.ai.intelliconf.common.database.BaseDO;
import com.jxl.ai.intelliconf.handler.EmailEncryptHandler;
import com.jxl.ai.intelliconf.handler.PhoneEncryptHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户持久层实体
 */
@TableName("sys_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO extends BaseDO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 邮箱
     */
    @EncryptField(type = "EMAIL")
    @TableField(typeHandler = EmailEncryptHandler.class)
    private String email;

    /**
     * 手机号码
     */
    @EncryptField(type = "PHONE")
    @TableField(typeHandler = PhoneEncryptHandler.class)
    private String phone;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 注销时间戳
     */
    private Long deleteTime;

}
