package com.jxl.ai.intelliconf.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会议成员关联 Mapper
 */
@Mapper
public interface ConfMemberMapper extends BaseMapper<ConfMemberDO> {

    /**
     * 根据会议ID和角色，联表查询收件人详细信息（去重）
     *
     * @param confId 会议ID
     * @param role   角色标识（如 AUTHOR, REVIEWER）
     * @return 收件人列表，包含 name / email / institution
     */
    List<ContactDTO> selectParticipantsByRole(@Param("confId") Long confId, @Param("role") String role);
}
