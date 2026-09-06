package com.jxl.ai.intelliconf.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName("conf_email_content")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfEmailContentDO {

    @TableId(value = "task_log_id")
    private Long taskLogId;

    private Long confId;

    private String subject;

    private String contentBody;

    private String targetRole;

    /**
     * 发送时间。为空时默认按当前时间触发。
     */
    private Date sendTime;

    /**
     * 内容状态：0=草稿，1=待发送，2=已发送，3=发送中。
     */
    private Integer contentStatus;

    private Date createTime;

    private Date updateTime;
}
