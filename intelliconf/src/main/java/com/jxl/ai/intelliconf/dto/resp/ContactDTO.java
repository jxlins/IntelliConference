package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联系人数据传输对象（跨表查询结果 & 批量导入请求体共用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContactDTO {

    private Long id;

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
}
