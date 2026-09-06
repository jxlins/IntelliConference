package com.jxl.ai.intelliconf.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class EmailContentSaveReqDTO {

    private Long taskLogId;

    private String subject;

    private String contentBody;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sendTime;
}
