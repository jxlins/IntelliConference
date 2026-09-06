package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailRespDTO {

    private TaskLogRespDTO task;

    private TaskBatchStatRespDTO batchStat;

    private EmailContentRespDTO emailContent;
}
