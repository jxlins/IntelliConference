package com.jxl.ai.intelliconf.service.impl;

import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConferencePermissionServiceImpl implements ConferencePermissionService {

    private final ConferenceMapper conferenceMapper;

    @Override
    public void requireAtLeastRole(Long confId, ConferenceRole requiredRole) {
        if (confId == null) {
            throw new ClientException("会议ID不能为空");
        }

        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(username)) {
            throw new ClientException("用户未登录或登录态已失效");
        }

        ConferenceDO conference = conferenceMapper.selectById(confId);
        if (conference == null || Integer.valueOf(1).equals(conference.getDelFlag())) {
            throw new ClientException("会议不存在");
        }

        if (StringUtils.hasText(userId) && userId.equals(conference.getCreateUser())) {
            return;
        }
        if (StringUtils.hasText(username) && username.equals(conference.getCreateUser())) {
            return;
        }
        throw new ClientException("当前用户无权限执行该操作");
    }
}
