package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.resp.ContactDTO;

import java.util.List;

/**
 * 会议成员服务接口
 */
public interface MemberService {

    /**
     * 根据会议ID和角色查询参与人员（含 name / email）
     *
     * @param confId 会议ID
     * @param role   角色标识，参考 EmailType.defaultRole
     * @return 收件人列表
     */
    List<ContactDTO> getParticipantsByRole(Long confId, String role);

    /**
     * 批量导入联系人并关联到会议
     * <p>
     * 邮箱在 contact_pool 已存在则更新 name/institution；否则插入。
     * 同时在 conf_member 中建立 (conf_id, contact_id, role) 关联（幂等，重复不插入）。
     * 所属组织者ID通过 {@link com.jxl.ai.intelliconf.common.biz.user.UserContext#getUserId()} 自动获取。
     *
     * @param confId   目标会议ID
     * @param contacts 联系人列表
     * @param role     导入时赋予的角色
     */
    void batchImportContacts(Long confId, List<ContactDTO> contacts, String role);
}
