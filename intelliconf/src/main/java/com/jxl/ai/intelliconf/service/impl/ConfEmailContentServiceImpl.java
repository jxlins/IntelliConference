package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfEmailContentMapper;
import com.jxl.ai.intelliconf.service.ConfEmailContentService;
import org.springframework.stereotype.Service;

@Service
public class ConfEmailContentServiceImpl extends ServiceImpl<ConfEmailContentMapper, ConfEmailContentDO>
        implements ConfEmailContentService {
}
