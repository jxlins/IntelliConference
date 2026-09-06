package com.jxl.ai.intelliconf.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class TaskActionLogRespDTO {
    private Long id;
    private Long taskInstanceId;
    private String operatorId;
    private String operatorName;
    private String operatorRoleCode;
    private String actionType;
    private String comment;
    private String beforeStatus;
    private String afterStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
