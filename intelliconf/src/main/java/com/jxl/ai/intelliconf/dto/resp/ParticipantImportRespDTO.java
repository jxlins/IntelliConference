package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantImportRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer successCount;

    private Integer skippedCount;

    private Integer failedCount;

    private Integer duplicateCount;

    private Integer totalCount;

    private String message;
}
