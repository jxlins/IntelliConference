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
@TableName("conf_author_discovery_job")
public class AuthorDiscoveryJobDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conferenceId;
    private String topicKeywords;
    private Integer yearFrom;
    private Integer yearTo;
    private Integer maxAuthors;
    private String status;
    private Integer totalPapers;
    private Integer totalCandidates;
    private Integer highConfidenceEmails;
    private String errorMessage;
    private Long createdBy;
    private Date createdAt;
    private Date startedAt;
    private Date finishedAt;
}
