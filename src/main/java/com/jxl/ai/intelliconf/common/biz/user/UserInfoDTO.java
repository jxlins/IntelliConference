package com.jxl.ai.intelliconf.common.biz.user;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {

    /**
     * 用户id
     */
    @JSONField(name = "id")
    private String userId;

    private String username;

    private String realName;

}
