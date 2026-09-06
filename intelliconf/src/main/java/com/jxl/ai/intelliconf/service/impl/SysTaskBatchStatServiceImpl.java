package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.dao.entity.SysTaskBatchStatDO;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskBatchStatMapper;
import com.jxl.ai.intelliconf.service.SysTaskBatchStatService;
import org.springframework.stereotype.Service;

@Service
public class SysTaskBatchStatServiceImpl extends ServiceImpl<SysTaskBatchStatMapper, SysTaskBatchStatDO>
        implements SysTaskBatchStatService {
}
