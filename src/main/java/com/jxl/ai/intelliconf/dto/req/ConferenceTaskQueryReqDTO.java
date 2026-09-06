package com.jxl.ai.intelliconf.dto.req;

import lombok.Data;

@Data
public class ConferenceTaskQueryReqDTO {
    private String nodeCode;
    private String status;
    private String priority;
    private String taskType;
    private String assigneeRoleCode;
    private String handlerUserId;
    private String keyword;
    private Integer pageNo = 1;
    private Integer pageSize = 50;
}
