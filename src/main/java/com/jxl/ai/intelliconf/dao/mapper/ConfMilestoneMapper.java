package com.jxl.ai.intelliconf.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会议时间持久层
 */
@Mapper
public interface ConfMilestoneMapper extends BaseMapper<ConfMilestoneDO> {

    /**
     * 根据会议ID和节点编码快速定位里程碑节点（用于前置依赖校验）
     *
     * @param confId   会议ID
     * @param nodeCode 节点编码
     * @return 里程碑记录，不存在则返回 null
     */
    default ConfMilestoneDO selectByNodeCode(@Param("confId") Long confId,
                                             @Param("nodeCode") String nodeCode) {
        return selectOne(
                Wrappers.lambdaQuery(ConfMilestoneDO.class)
                        .eq(ConfMilestoneDO::getConfereId, confId)
                        .eq(ConfMilestoneDO::getNodeCode, nodeCode)
        );
    }
}
