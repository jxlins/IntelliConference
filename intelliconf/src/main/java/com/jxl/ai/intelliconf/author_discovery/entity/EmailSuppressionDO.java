package com.jxl.ai.intelliconf.author_discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conf_email_suppression")
public class EmailSuppressionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conferenceId;
    private String email;
    private String reason;
    private Date createdAt;
}
