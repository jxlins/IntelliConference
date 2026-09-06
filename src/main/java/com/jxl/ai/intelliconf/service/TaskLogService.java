package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;

/**
 * 任务执行日志服务接口
 * <p>
 * 注意：TaskDispatcher 负责主执行链路（状态 1→2/3）的日志生命周期管理。
 * 本接口供其他组件（如 Handler）查询或追加补充信息使用。
 */
public interface TaskLogService extends IService<SysTaskLogDO> {

    /**
     * 向指定日志记录追加结果摘要（追加到 error_msg 字段，也用于成功时的结果备注）
     *
     * @param logId  sys_task_log 主键
     * @param remark 要追加的摘要文本
     */
    void appendRemark(Long logId, String remark);
}
