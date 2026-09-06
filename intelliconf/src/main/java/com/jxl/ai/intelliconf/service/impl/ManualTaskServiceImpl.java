package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.service.MilestoneCompletionChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualTaskServiceImpl {

    private final SysTaskLogMapper sysTaskLogMapper;
    private final MilestoneCompletionChecker milestoneCompletionChecker;

    /**
     * 当组织者手动完成任务时调用
     * @param taskLogId sys_task_log 表的主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskLogId) {
        // 1. 获取任务日志
        SysTaskLogDO taskLog = sysTaskLogMapper.selectById(taskLogId);
        if (taskLog == null || taskLog.getExecutionStatus() == 2) {
            return;
        }

        // 2. 将状态从 1 (执行中) 更新为 2 (已完成)
        taskLog.setExecutionStatus(2);
        taskLog.setEndTime(new Date());
        sysTaskLogMapper.updateById(taskLog);

        log.info("[ManualTaskService] Manual task log {} marked as completed.", taskLogId);

        // 3. 核心：触发里程碑达成检查
        // 检查这个任务是否是该里程碑下的最后一个未完成任务
        milestoneCompletionChecker.checkAndComplete(taskLog.getMilestoneId());
    }
}