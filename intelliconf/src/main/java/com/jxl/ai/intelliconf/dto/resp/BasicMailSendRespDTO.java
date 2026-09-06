package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasicMailSendRespDTO {

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private List<String> successEmails;

    private List<String> failedDetails;
}
