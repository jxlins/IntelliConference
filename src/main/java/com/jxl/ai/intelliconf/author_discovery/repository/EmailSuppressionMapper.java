package com.jxl.ai.intelliconf.author_discovery.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jxl.ai.intelliconf.author_discovery.entity.EmailSuppressionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailSuppressionMapper extends BaseMapper<EmailSuppressionDO> {
}
