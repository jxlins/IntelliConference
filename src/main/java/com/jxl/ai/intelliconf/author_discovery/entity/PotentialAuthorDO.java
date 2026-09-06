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
@TableName("conf_potential_author")
public class PotentialAuthorDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conferenceId;
    private Long discoveryJobId;
    private String authorName;
    private String normalizedName;
    private String email;
    private String organization;
    private String countryRegion;
    private String researchKeywords;
    private String representativePapers;
    private String sourcePlatform;
    private String sourceUrl;
    private BigDecimal topicSimilarity;
    private BigDecimal emailConfidence;
    private BigDecimal overallScore;
    private String reviewStatus;
    private String contactStatus;
    private Date createdAt;
    private Date updatedAt;
}
