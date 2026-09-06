package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会议里程碑列表响应参数（含全局确认状态）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneListRespDTO {

    /**
     * 是否所有节点均已确认（前端可据此切换只读模式）
     */
    private Boolean allConfirmed;

    /**
     * 里程碑节点列表（按 start_date 升序）
     */
    private List<MilestoneRespDTO> milestones;
}
