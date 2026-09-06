package com.jxl.ai.intelliconf.service;

public interface ManualTaskService {

    /**
     * 当组织者手动完成任务时调用
     * @param taskLogId sys_task_log 表的主键 ID
     */
    void completeTask(Long taskLogId);
}
