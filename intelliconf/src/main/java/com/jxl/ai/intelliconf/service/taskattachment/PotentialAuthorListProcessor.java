package com.jxl.ai.intelliconf.service.taskattachment;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.constant.ConferenceTaskConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfContactPoolDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfContactPoolMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.req.ParticipantImportExcelDTO;
import com.jxl.ai.intelliconf.handler.ParticipantImportListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PotentialAuthorListProcessor implements TaskAttachmentProcessor {

    private static final String MEMBER_ROLE_PROSPECT = "PROSPECT";

    private final ConferenceMapper conferenceMapper;
    private final ConfContactPoolMapper contactPoolMapper;
    private final ConfMemberMapper memberMapper;

    @Override
    public boolean supports(String taskCode) {
        return ConferenceTaskConstant.TASK_CODE_IMPORT_POTENTIAL_AUTHOR_LIST.equals(taskCode);
    }

    @Override
    public TaskAttachmentProcessResult process(TaskAttachmentProcessContext context) {
        ConferenceDO conference = conferenceMapper.selectById(context.getConferenceId());
        if (conference == null) {
            throw new ClientException("Conference not found");
        }

        ParticipantImportListener listener = new ParticipantImportListener();
        try {
            EasyExcel.read(context.getFile().getInputStream(), ParticipantImportExcelDTO.class, listener)
                    .sheet()
                    .headRowNumber(0)
                    .doRead();
        } catch (IOException ex) {
            throw new ClientException("Failed to parse potential author list: " + ex.getMessage());
        }

        List<ParticipantImportExcelDTO> validDataList = listener.getAllDataList();
        int invalidCount = listener.getSkippedCount();
        if (validDataList.isEmpty()) {
            return buildResult(0, 0, invalidCount, 0);
        }

        Long ownerOrgId = resolveOwnerOrgId(conference, context.getConferenceId(), context.getOperatorId());
        Map<String, Long> emailToContactIdMap = batchUpsertContacts(ownerOrgId, validDataList);
        int insertedMemberCount = batchInsertMembers(context.getConferenceId(), MEMBER_ROLE_PROSPECT, emailToContactIdMap);
        int duplicateCount = Math.max(validDataList.size() - insertedMemberCount, 0);
        int totalCount = validDataList.size() + invalidCount;
        return buildResult(totalCount, insertedMemberCount, invalidCount, duplicateCount);
    }

    private TaskAttachmentProcessResult buildResult(int totalCount, int successCount, int failedCount, int duplicateCount) {
        String processStatus = successCount == 0
                ? ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_FAILED
                : (failedCount > 0
                ? ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_PARTIAL_SUCCESS
                : ConferenceTaskConstant.ATTACHMENT_PROCESS_STATUS_SUCCESS);
        String processMessage = String.format("共 %d 条，成功 %d 条，失败 %d 条，重复 %d 条。",
                totalCount, successCount, failedCount, duplicateCount);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("totalCount", totalCount);
        resultMap.put("successCount", successCount);
        resultMap.put("failedCount", failedCount);
        resultMap.put("duplicateCount", duplicateCount);
        resultMap.put("processStatus", processStatus);
        return TaskAttachmentProcessResult.builder()
                .processType(ConferenceTaskConstant.ATTACHMENT_PROCESS_TYPE_POTENTIAL_AUTHOR_IMPORT)
                .processStatus(processStatus)
                .processMessage(processMessage)
                .processResult(JSON.toJSONString(resultMap))
                .totalCount(totalCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .duplicateCount(duplicateCount)
                .build();
    }

    private Long resolveOwnerOrgId(ConferenceDO conference, Long confId, Long operatorId) {
        if (operatorId != null) {
            return operatorId;
        }
        String createUser = conference == null ? null : conference.getCreateUser();
        if (StrUtil.isNotBlank(createUser) && createUser.matches("\\d+")) {
            return Long.parseLong(createUser);
        }
        return confId == null ? 0L : confId;
    }

    private Map<String, Long> batchUpsertContacts(Long ownerOrgId, List<ParticipantImportExcelDTO> dataList) {
        Map<String, Long> emailToContactIdMap = new HashMap<>();
        dataList.forEach(item -> item.setEmail(normalizeEmail(item.getEmail())));

        Set<String> emailSet = dataList.stream()
                .map(ParticipantImportExcelDTO::getEmail)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (emailSet.isEmpty()) {
            return emailToContactIdMap;
        }

        List<ConfContactPoolDO> existingContacts = contactPoolMapper.selectList(
                Wrappers.lambdaQuery(ConfContactPoolDO.class)
                        .eq(ConfContactPoolDO::getOwnerOrgId, ownerOrgId)
                        .in(ConfContactPoolDO::getEmail, emailSet)
        );

        Map<String, ConfContactPoolDO> existingEmailMap = existingContacts.stream()
                .collect(Collectors.toMap(ConfContactPoolDO::getEmail, item -> item, (left, right) -> left));

        List<ConfContactPoolDO> toInsert = new ArrayList<>();
        List<ConfContactPoolDO> toUpdate = new ArrayList<>();
        for (ParticipantImportExcelDTO data : dataList) {
            String email = data.getEmail();
            ConfContactPoolDO existing = existingEmailMap.get(email);
            if (existing != null) {
                existing.setName(data.getName());
                existing.setInstitution(data.getInstitution());
                toUpdate.add(existing);
                emailToContactIdMap.put(email, existing.getId());
                continue;
            }
            ConfContactPoolDO newContact = ConfContactPoolDO.builder()
                    .ownerOrgId(ownerOrgId)
                    .name(data.getName())
                    .email(email)
                    .institution(data.getInstitution())
                    .build();
            toInsert.add(newContact);
        }

        for (ConfContactPoolDO contact : toInsert) {
            contactPoolMapper.insert(contact);
            emailToContactIdMap.put(contact.getEmail(), contact.getId());
        }
        for (ConfContactPoolDO contact : toUpdate) {
            contactPoolMapper.updateById(contact);
        }
        return emailToContactIdMap;
    }

    private int batchInsertMembers(Long confId, String role, Map<String, Long> emailToContactIdMap) {
        Set<Long> contactIds = new HashSet<>(emailToContactIdMap.values());
        if (contactIds.isEmpty()) {
            return 0;
        }
        List<ConfMemberDO> existingMembers = memberMapper.selectList(
                Wrappers.lambdaQuery(ConfMemberDO.class)
                        .eq(ConfMemberDO::getConfId, confId)
                        .eq(ConfMemberDO::getRole, role)
                        .in(ConfMemberDO::getContactId, contactIds)
        );
        Set<Long> existingContactIds = existingMembers.stream()
                .map(ConfMemberDO::getContactId)
                .collect(Collectors.toSet());
        List<ConfMemberDO> toInsert = contactIds.stream()
                .filter(contactId -> !existingContactIds.contains(contactId))
                .map(contactId -> ConfMemberDO.builder()
                        .confId(confId)
                        .contactId(contactId)
                        .role(role)
                        .build())
                .toList();
        for (ConfMemberDO member : toInsert) {
            memberMapper.insert(member);
        }
        return toInsert.size();
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? email : email.trim().toLowerCase(Locale.ROOT);
    }
}
