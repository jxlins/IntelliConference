package com.jxl.ai.intelliconf.dto.req;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 参会者导入 Excel DTO
 */
@Data
public class ParticipantImportExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名
     */
    @ExcelProperty(value = "姓名", index = 0)
    private String name;

    /**
     * 邮箱
     */
    @ExcelProperty(value = "邮箱", index = 1)
    private String email;

    /**
     * 单位/机构
     */
    @ExcelProperty(value = "单位", index = 2)
    private String institution;
}
