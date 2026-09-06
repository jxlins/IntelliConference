package com.jxl.ai.intelliconf.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteePrincipal;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConfContactPoolDO;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfContactPoolMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberMapper;
import com.jxl.ai.intelliconf.dto.req.CommitteeMemberInputReqDTO;
import com.jxl.ai.intelliconf.enums.CommitteeRole;
import com.jxl.ai.intelliconf.service.PortalAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortalAccessServiceImpl implements PortalAccessService {

    private static final String STATUS_INVITED = "INVITED";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DECLINED = "DECLINED";
    private static final long DEFAULT_TOKEN_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

    private final ConfCommitteeMapper confCommitteeMapper;
    private final ConfMemberMapper confMemberMapper;
    private final ConfContactPoolMapper confContactPoolMapper;

    @Override
    public String generateToken(String email, String role) {
        if (!StringUtils.hasText(email)) {
            throw new ClientException("邮箱不能为空");
        }
        CommitteeRole committeeRole = parseRole(role);

        ConfCommitteeDO committee = confCommitteeMapper.selectOne(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getEmail, email.trim().toLowerCase())
                        .eq(ConfCommitteeDO::getRole, committeeRole.name())
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .orderByDesc(ConfCommitteeDO::getId)
                        .last("LIMIT 1")
        );

        if (committee == null) {
            throw new ClientException("委员会成员不存在，请先添加到会议");
        }

        String token = nextToken();
        Date expireTime = committee.getTokenExpireTime();
        if (expireTime == null || expireTime.before(new Date())) {
            expireTime = defaultExpireTime();
        }

        confCommitteeMapper.updateById(ConfCommitteeDO.builder()
                .id(committee.getId())
                .accessToken(token)
                .tokenExpireTime(expireTime)
            .inviteStatus(STATUS_INVITED)
                .build());
        return token;
    }

    @Override
    public String generateToken(Long confId, String email, String name, String role, Date tokenExpireTime) {
        if (confId == null) {
            throw new ClientException("会议ID不能为空");
        }
        if (!StringUtils.hasText(email)) {
            throw new ClientException("邮箱不能为空");
        }
        CommitteeRole committeeRole = parseRole(role);

        Date expireTime = tokenExpireTime == null ? defaultExpireTime() : tokenExpireTime;
        if (expireTime.before(new Date())) {
            throw new ClientException("令牌过期时间不能早于当前时间");
        }

        String normalizedEmail = email.trim().toLowerCase();
        ensureNotParticipant(confId, normalizedEmail);
        String token = nextToken();

        ConfCommitteeDO existing = confCommitteeMapper.selectOne(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getConfId, confId)
                        .eq(ConfCommitteeDO::getEmail, normalizedEmail)
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .last("LIMIT 1")
        );

        if (existing == null) {
            ConfCommitteeDO toCreate = ConfCommitteeDO.builder()
                    .confId(confId)
                    .email(normalizedEmail)
                    .name(name)
                    .institution(null)
                    .role(committeeRole.name())
                    .inviteStatus(STATUS_INVITED)
                    .accessToken(token)
                    .tokenExpireTime(expireTime)
                    .build();
            toCreate.setDelFlag(0);
            confCommitteeMapper.insert(toCreate);
            return token;
        }

        ConfCommitteeDO toUpdate = ConfCommitteeDO.builder()
                .id(existing.getId())
                .name(StringUtils.hasText(name) ? name.trim() : existing.getName())
                .institution(existing.getInstitution())
                .role(committeeRole.name())
            .inviteStatus(STATUS_INVITED)
                .accessToken(token)
                .tokenExpireTime(expireTime)
                .build();
        toUpdate.setDelFlag(0);
        confCommitteeMapper.updateById(toUpdate);
        return token;
    }

    @Override
    public PortalCommitteePrincipal validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        ConfCommitteeDO committee = confCommitteeMapper.selectOne(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getAccessToken, token.trim())
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .last("LIMIT 1")
        );
        if (committee == null) {
            return null;
        }
        if (committee.getTokenExpireTime() == null || committee.getTokenExpireTime().before(new Date())) {
            return null;
        }

        return PortalCommitteePrincipal.builder()
                .id(committee.getId())
                .confId(committee.getConfId())
                .email(committee.getEmail())
                .name(committee.getName())
                .role(committee.getRole())
                .inviteStatus(committee.getInviteStatus())
                .accessToken(committee.getAccessToken())
                .tokenExpireTime(committee.getTokenExpireTime())
                .build();
    }

    @Override
    public void decideByToken(String token, boolean agree) {
        if (!StringUtils.hasText(token)) {
            throw new ClientException("门户令牌不能为空");
        }

        ConfCommitteeDO committee = confCommitteeMapper.selectOne(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getAccessToken, token.trim())
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .last("LIMIT 1")
        );
        if (committee == null) {
            throw new ClientException("门户令牌无效");
        }
        if (committee.getTokenExpireTime() == null || committee.getTokenExpireTime().before(new Date())) {
            throw new ClientException("门户令牌已过期");
        }

        // 仅允许首次从 INVITED 决策，避免重复点击覆盖已确认结果。
        if (!STATUS_INVITED.equalsIgnoreCase(committee.getInviteStatus())) {
            return;
        }

        confCommitteeMapper.update(
                null,
                Wrappers.lambdaUpdate(ConfCommitteeDO.class)
                        .set(ConfCommitteeDO::getInviteStatus, agree ? STATUS_ACCEPTED : STATUS_DECLINED)
                        .eq(ConfCommitteeDO::getId, committee.getId())
                        .eq(ConfCommitteeDO::getInviteStatus, STATUS_INVITED)
        );
    }

    @Override
    public List<ConfCommitteeDO> listByConferenceId(Long confId) {
        if (confId == null) {
            return new ArrayList<>();
        }
        return confCommitteeMapper.selectList(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getConfId, confId)
                .eq(ConfCommitteeDO::getInviteStatus, STATUS_ACCEPTED)
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .orderByAsc(ConfCommitteeDO::getId)
        );
    }

    @Override
    public List<ConfCommitteeDO> listAllByConferenceId(Long confId) {
        if (confId == null) {
            return new ArrayList<>();
        }
        return confCommitteeMapper.selectList(
                Wrappers.lambdaQuery(ConfCommitteeDO.class)
                        .eq(ConfCommitteeDO::getConfId, confId)
                        .eq(ConfCommitteeDO::getDelFlag, 0)
                        .orderByAsc(ConfCommitteeDO::getId)
        );
    }

    @Override
    public int importMembersByChairToken(String token, List<CommitteeMemberInputReqDTO> members) {
        PortalCommitteePrincipal principal = validateToken(token);
        if (principal == null) {
            throw new ClientException("门户令牌无效或已过期");
        }
        if (!STATUS_ACCEPTED.equalsIgnoreCase(principal.getInviteStatus())) {
            throw new ClientException("请先确认担任委员会主席");
        }
        if (!CommitteeRole.CHAIR.name().equalsIgnoreCase(principal.getRole())) {
            throw new ClientException("仅委员会主席可录入委员会名单");
        }
        if (members == null || members.isEmpty()) {
            throw new ClientException("请输入至少一条委员会成员信息");
        }

        List<CommitteeMemberInputReqDTO> sanitized = members.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getEmail()))
                .map(item -> {
                    CommitteeMemberInputReqDTO dto = new CommitteeMemberInputReqDTO();
                    dto.setEmail(item.getEmail().trim().toLowerCase());
                    dto.setName(StringUtils.hasText(item.getName()) ? item.getName().trim() : null);
                    dto.setInstitution(StringUtils.hasText(item.getInstitution()) ? item.getInstitution().trim() : null);
                    return dto;
                })
                .collect(Collectors.toList());

        if (sanitized.isEmpty()) {
            throw new ClientException("有效成员邮箱不能为空");
        }

        Map<String, CommitteeMemberInputReqDTO> deduplicated = new LinkedHashMap<>();
        for (CommitteeMemberInputReqDTO item : sanitized) {
            deduplicated.put(item.getEmail(), item);
        }

        int changed = 0;
        for (CommitteeMemberInputReqDTO item : deduplicated.values()) {
            ensureNotParticipant(principal.getConfId(), item.getEmail());

            String role = item.getEmail().equalsIgnoreCase(principal.getEmail())
                    ? CommitteeRole.CHAIR.name()
                    : CommitteeRole.REVIEWER.name();

            ConfCommitteeDO existing = confCommitteeMapper.selectOne(
                    Wrappers.lambdaQuery(ConfCommitteeDO.class)
                            .eq(ConfCommitteeDO::getConfId, principal.getConfId())
                            .eq(ConfCommitteeDO::getEmail, item.getEmail())
                            .eq(ConfCommitteeDO::getDelFlag, 0)
                            .last("LIMIT 1")
            );

            if (existing == null) {
                ConfCommitteeDO toCreate = ConfCommitteeDO.builder()
                        .confId(principal.getConfId())
                        .email(item.getEmail())
                        .name(item.getName())
                        .institution(item.getInstitution())
                        .role(role)
                        .inviteStatus(STATUS_ACCEPTED)
                        .accessToken(item.getEmail().equalsIgnoreCase(principal.getEmail()) ? principal.getAccessToken() : null)
                        .tokenExpireTime(item.getEmail().equalsIgnoreCase(principal.getEmail()) ? principal.getTokenExpireTime() : null)
                        .build();
                toCreate.setDelFlag(0);
                changed += confCommitteeMapper.insert(toCreate);
                continue;
            }

            ConfCommitteeDO toUpdate = ConfCommitteeDO.builder()
                    .id(existing.getId())
                    .name(StringUtils.hasText(item.getName()) ? item.getName() : existing.getName())
                    .institution(StringUtils.hasText(item.getInstitution()) ? item.getInstitution() : existing.getInstitution())
                    .role(item.getEmail().equalsIgnoreCase(principal.getEmail()) ? CommitteeRole.CHAIR.name() : existing.getRole())
                    .inviteStatus(STATUS_ACCEPTED)
                    .build();
            toUpdate.setDelFlag(0);
            changed += confCommitteeMapper.updateById(toUpdate);
        }

        return changed;
    }

    private CommitteeRole parseRole(String role) {
        CommitteeRole committeeRole = CommitteeRole.fromCode(role);
        if (committeeRole == null) {
            throw new ClientException("委员会角色不合法，允许值：CHAIR/REVIEWER");
        }
        return committeeRole;
    }

    private String nextToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Date defaultExpireTime() {
        return new Date(System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS);
    }

    private void ensureNotParticipant(Long confId, String email) {
        List<ConfMemberDO> members = confMemberMapper.selectList(
                Wrappers.lambdaQuery(ConfMemberDO.class)
                        .eq(ConfMemberDO::getConfId, confId)
        );
        if (members.isEmpty()) {
            return;
        }

        Set<Long> contactIds = members.stream()
                .map(ConfMemberDO::getContactId)
                .collect(Collectors.toSet());
        if (contactIds.isEmpty()) {
            return;
        }

        Long cnt = confContactPoolMapper.selectCount(
                Wrappers.lambdaQuery(ConfContactPoolDO.class)
                        .in(ConfContactPoolDO::getId, contactIds)
                        .eq(ConfContactPoolDO::getEmail, email)
        );
        if (cnt != null && cnt > 0) {
            throw new ClientException("该邮箱已是会议参会者，不能加入委员会");
        }
    }
}
