package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.dao.entity.SysMailLogDO;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.service.SysMailLogService;
import org.springframework.stereotype.Service;

@Service
public class SysMailLogServiceImpl extends ServiceImpl<SysMailLogMapper, SysMailLogDO>
        implements SysMailLogService {
}
