package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.dao.entity.ConfContactPoolDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfContactPoolMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberMapper;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会议成员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final ConfMemberMapper confMemberMapper;
    private final ConfContactPoolMapper confContactPoolMapper;

    @Override
    public List<ContactDTO> getParticipantsByRole(Long confId, String role) {
        return confMemberMapper.selectParticipantsByRole(confId, role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchImportContacts(Long confId, List<ContactDTO> contacts, String role) {
        Long orgId = Long.parseLong(UserContext.getUserId());
        int inserted = 0;
        int updated = 0;

        for (ContactDTO contact : contacts) {
            // 第一步：在 contact_pool 中 upsert（以 owner_org_id + email 为唯一键）
            ConfContactPoolDO existing = confContactPoolMapper.selectOne(
                    Wrappers.lambdaQuery(ConfContactPoolDO.class)
                            .eq(ConfContactPoolDO::getOwnerOrgId, orgId)
                            .eq(ConfContactPoolDO::getEmail, contact.getEmail())
            );

            Long contactId;
            if (existing != null) {
                // 已存在：更新 name / institution
                confContactPoolMapper.update(null,
                        Wrappers.lambdaUpdate(ConfContactPoolDO.class)
                                .set(ConfContactPoolDO::getName, contact.getName())
                                .set(ConfContactPoolDO::getInstitution, contact.getInstitution())
                                .eq(ConfContactPoolDO::getId, existing.getId())
                );
                contactId = existing.getId();
                updated++;
            } else {
                // 不存在：插入新记录
                ConfContactPoolDO newContact = ConfContactPoolDO.builder()
                        .ownerOrgId(orgId)
                        .name(contact.getName())
                        .email(contact.getEmail())
                        .institution(contact.getInstitution())
                        .build();
                confContactPoolMapper.insert(newContact);
                contactId = newContact.getId();
                inserted++;
            }

            // 第二步：在 conf_member 中建立关联（幂等检查）
            boolean alreadyLinked = confMemberMapper.exists(
                    Wrappers.lambdaQuery(ConfMemberDO.class)
                            .eq(ConfMemberDO::getConfId, confId)
                            .eq(ConfMemberDO::getContactId, contactId)
            );
            if (!alreadyLinked) {
                confMemberMapper.insert(ConfMemberDO.builder()
                        .confId(confId)
                        .contactId(contactId)
                        .role(role)
                        .build());
            }
        }

        log.info("[MemberService] 批量导入完成, confId={}, role={}, 新增={}, 更新={}",
                confId, role, inserted, updated);
    }
}
