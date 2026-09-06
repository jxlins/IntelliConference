package com.jxl.ai.intelliconf.author_discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conf_author_email_source")
public class AuthorEmailSourceDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long potentialAuthorId;
    private String email;
    private String sourceType;
    private String sourceUrl;
    private String evidenceText;
    private BigDecimal confidence;
    private Date collectedAt;
}
