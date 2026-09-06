package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.service.TaskLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务执行日志服务实现
 */
@Slf4j
@Service
public class TaskLogServiceImpl extends ServiceImpl<SysTaskLogMapper, SysTaskLogDO>
        implements TaskLogService {

    @Override
    public void appendRemark(Long logId, String remark) {
        if (logId == null) {
            log.warn("[TaskLogService] logId 为 null，跳过 appendRemark");
            return;
        }
        updateById(SysTaskLogDO.builder()
                .id(logId)
                .errorMsg(remark)
                .build());
    }
}
