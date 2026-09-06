package com.jxl.ai.intelliconf.handler;

public interface TaskHandler {
    /**
     * 执行具体业务逻辑
     * @param confId 会议ID
     * @param taskLogId 当前任务日志ID
     * @param params 任务定义的JSON参数
     * @throws Exception 执行失败抛出异常，由引擎统一捕获并记录日志
     */
    void execute(Long confId, Long taskLogId, String params) throws Exception;
}