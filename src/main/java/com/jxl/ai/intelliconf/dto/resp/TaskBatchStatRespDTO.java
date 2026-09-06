package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskBatchStatRespDTO {

    private Long taskLogId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;
}
