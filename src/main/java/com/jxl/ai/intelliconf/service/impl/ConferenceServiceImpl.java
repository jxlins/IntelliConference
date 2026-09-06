package com.jxl.ai.intelliconf.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.constant.ConferenceSetupConstant;
import com.jxl.ai.intelliconf.common.constant.RedisCacheConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.dao.entity.ConfCommitteeDO;
import com.jxl.ai.intelliconf.dao.entity.ConfEmailContentDO;
import com.jxl.ai.intelliconf.dao.entity.ConfLocalDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMemberDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceMailAccountDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.entity.MailSendLogDO;
import com.jxl.ai.intelliconf.dao.entity.MailSendTaskDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.entity.SysMailLogDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskBatchStatDO;
import com.jxl.ai.intelliconf.dao.entity.SysTaskLogDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfCommitteeMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfEmailContentMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfLocalMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMemberMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMailAccountMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailSendTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysMailLogMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskBatchStatMapper;
import com.jxl.ai.intelliconf.dao.mapper.SysTaskLogMapper;
import com.jxl.ai.intelliconf.dto.req.ConferenceCreateRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceImportantDatesReqDTO;
import com.jxl.ai.intelliconf.dto.req.ConferencePageRepDTO;
import com.jxl.ai.intelliconf.dto.req.ConferenceUpdateRepDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferencePageRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceSetupStatusRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.enums.ConferenceStatus;
import com.jxl.ai.intelliconf.event.ConferenceStatusChangeEvent;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConferenceService;
import com.jxl.ai.intelliconf.service.ConferenceTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

import static com.jxl.ai.intelliconf.common.constant.ConferenceStateConstant.PREPARING;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenceServiceImpl extends ServiceImpl<ConferenceMapper, ConferenceDO> implements ConferenceService {

    private final ConfLocalMapper confLocalMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final ConfMilestoneMapper confMilestoneMapper;
    private final ConfStageMapper confStageMapper;
    private final ConfMemberMapper confMemberMapper;
    private final ConfCommitteeMapper confCommitteeMapper;
    private final ConfEmailContentMapper confEmailContentMapper;
    private final SysTaskLogMapper sysTaskLogMapper;
    private final SysTaskBatchStatMapper sysTaskBatchStatMapper;
    private final SysMailLogMapper sysMailLogMapper;
    private final MailTemplateMapper mailTemplateMapper;
    private final MailSendTaskMapper mailSendTaskMapper;
    private final MailSendLogMapper mailSendLogMapper;
    private final ConferenceMailAccountMapper conferenceMailAccountMapper;
    private final ConferenceTaskService conferenceTaskService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    @Override
    public void createConference(ConferenceCreateRepDTO requestParam) {
        ConferenceDO conferenceDO = BeanUtil.toBean(requestParam, ConferenceDO.class);
        conferenceDO.setCurrentState(PREPARING);
        conferenceDO.setSetupStatus(ConferenceSetupConstant.BASIC_CREATED);
        conferenceDO.setCreateUser(resolveCreatorIdentity());
        try {
            baseMapper.insert(conferenceDO);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("会议简称已存在");
        }
        if (conferenceDO.getId() == null) {
            throw new ServiceException("会议创建失败");
        }

        Long newConfId = conferenceDO.getId();
        ConfLocalDO confLocalDO = BeanUtil.toBean(requestParam, ConfLocalDO.class);
        confLocalDO.setConferId(newConfId);
        if (confLocalMapper.insert(confLocalDO) < 1) {
            throw new ServiceException("会议地点创建失败");
        }

        log.info("Conference basic info created, conferenceId={}", newConfId);
    }

    private String resolveCreatorIdentity() {
        String userId = UserContext.getUserId();
        return StringUtils.hasText(userId) ? userId : UserContext.getUsername();
    }

    @Transactional
    @Override
    public void updateConference(ConferenceUpdateRepDTO requestParam) {
        validateRequiredBasicInfo(requestParam);

        LambdaQueryWrapper<ConferenceDO> queryWrapper = Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getShortName, requestParam.getShortName())
                .eq(ConferenceDO::getDelFlag, 0);
        ConferenceDO conferenceDO = baseMapper.selectOne(queryWrapper);
        if (conferenceDO == null) {
            throw new ServiceException("会议不存在");
        }

        conferencePermissionService.requireAtLeastRole(conferenceDO.getId(), ConferenceRole.COMMITTEE);

        ConferenceDO updateConference = BeanUtil.toBean(requestParam, ConferenceDO.class);
        LambdaUpdateWrapper<ConferenceDO> conferenceWrapper = new LambdaUpdateWrapper<ConferenceDO>()
                .eq(ConferenceDO::getShortName, requestParam.getShortName())
                .eq(ConferenceDO::getDelFlag, 0);
        if (baseMapper.update(updateConference, conferenceWrapper) < 1) {
            throw new ServiceException("会议更新失败");
        }

        ConfLocalDO confLocalDO = BeanUtil.toBean(requestParam, ConfLocalDO.class);
        LambdaQueryWrapper<ConfLocalDO> localWrapper = Wrappers.lambdaQuery(ConfLocalDO.class)
                .eq(ConfLocalDO::getConferId, conferenceDO.getId())
                .eq(ConfLocalDO::getDelFlag, 0);
        confLocalMapper.update(confLocalDO, localWrapper);
    }

    @Override
    public ConferenceDO resolveConference(String conferenceKey) {
        if (!StringUtils.hasText(conferenceKey)) {
            throw new ClientException("Conference id or short name is required");
        }
        LambdaQueryWrapper<ConferenceDO> queryWrapper = Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getDelFlag, 0);
        if (conferenceKey.matches("^\\d+$")) {
            queryWrapper.eq(ConferenceDO::getId, Long.valueOf(conferenceKey));
        } else {
            queryWrapper.eq(ConferenceDO::getShortName, conferenceKey);
        }
        ConferenceDO conference = baseMapper.selectOne(queryWrapper);
        if (conference == null) {
            throw new ClientException("Conference not found");
        }
        return conference;
    }

    @Override
    public ConferenceSetupStatusRespDTO getConferenceSetupStatus(String conferenceKey) {
        ConferenceDO conference = resolveConference(conferenceKey);
        conferencePermissionService.requireAtLeastRole(conference.getId(), ConferenceRole.COMMITTEE);
        boolean datesCompleted = importantDatesCompleted(conference);
        boolean stagesGenerated = ConferenceSetupConstant.STAGE_GENERATED.equals(conference.getSetupStatus())
                || ConferenceSetupConstant.TASK_GENERATED.equals(conference.getSetupStatus());
        String setupStatus = conference.getSetupStatus();
        if (!StringUtils.hasText(setupStatus)) {
            setupStatus = datesCompleted ? ConferenceSetupConstant.DATES_COMPLETED : ConferenceSetupConstant.BASIC_CREATED;
        }
        String nextAction = !datesCompleted
                ? ConferenceSetupConstant.NEXT_COMPLETE_DATES
                : (stagesGenerated ? ConferenceSetupConstant.NEXT_ENTER_DASHBOARD : ConferenceSetupConstant.NEXT_GENERATE_STAGES);
        return ConferenceSetupStatusRespDTO.builder()
                .conferenceId(conference.getId())
                .shortName(conference.getShortName())
                .title(conference.getTitle())
                .setupStatus(setupStatus)
                .datesCompleted(datesCompleted)
                .stagesGenerated(stagesGenerated)
                .needCompleteDates(!datesCompleted)
                .needGenerateStages(datesCompleted && !stagesGenerated)
                .nextAction(nextAction)
                .build();
    }

    @Transactional
    @Override
    public ConferenceRespDTO updateImportantDates(String conferenceKey, ConferenceImportantDatesReqDTO requestParam) {
        ConferenceDO conference = resolveConference(conferenceKey);
        conferencePermissionService.requireAtLeastRole(conference.getId(), ConferenceRole.COMMITTEE);
        validateImportantDates(requestParam);

        ConferenceDO update = new ConferenceDO();
        update.setPaperSubmissionDeadline(requestParam.getPaperSubmissionDeadline());
        update.setNotificationOfAcceptance(requestParam.getNotificationOfAcceptance());
        update.setCameraReadySubmission(requestParam.getCameraReadySubmission());
        update.setEarlyBirdRegistration(requestParam.getEarlyBirdRegistration());
        update.setConferenceStartDate(requestParam.getConferenceStartDate());
        update.setConferenceEndDate(requestParam.getConferenceEndDate());
        update.setSetupStatus(ConferenceSetupConstant.DATES_COMPLETED);
        LambdaUpdateWrapper<ConferenceDO> updateWrapper = Wrappers.lambdaUpdate(ConferenceDO.class)
                .eq(ConferenceDO::getId, conference.getId())
                .eq(ConferenceDO::getDelFlag, 0);
        if (baseMapper.update(update, updateWrapper) < 1) {
            throw new ServiceException("Failed to update conference important dates");
        }
        evictStageCacheAfterCommit(conference.getId());
        conferenceTaskService.evictGeneratedTaskCacheAfterCommit(conference.getId());
        log.info("Conference important dates updated, conferenceId={}, operator={}",
                conference.getId(), UserContext.getUsername());
        return getConferenceByShortName(conference.getShortName());
    }

    @Override
    public void switchState(Long confId, String targetState) {
        ConferenceDO conference = baseMapper.selectById(confId);
        if (conference == null) {
            throw new ServiceException("会议不存在, confId=" + confId);
        }
        LambdaUpdateWrapper<ConferenceDO> updateWrapper = Wrappers.lambdaUpdate(ConferenceDO.class)
                .set(ConferenceDO::getCurrentState, targetState)
                .eq(ConferenceDO::getId, confId);
        if (baseMapper.update(null, updateWrapper) < 1) {
            throw new ServiceException("会议状态切换失败, confId=" + confId + ", targetState=" + targetState);
        }
        log.info("Conference state switched, conferenceId={}, from={}, to={}",
                confId, conference.getCurrentState(), targetState);
    }

    @Override
    public void updateStatus(Long confId, ConferenceStatus status) {
        ConferenceDO conference = baseMapper.selectById(confId);
        if (conference == null) {
            log.warn("Conference not found when updating status, conferenceId={}", confId);
            return;
        }
        String oldStatus = conference.getCurrentState();
        String newStatus = status.getStatus();
        if (newStatus.equals(oldStatus)) {
            return;
        }

        LambdaUpdateWrapper<ConferenceDO> updateWrapper = Wrappers.lambdaUpdate(ConferenceDO.class)
                .set(ConferenceDO::getCurrentState, newStatus)
                .eq(ConferenceDO::getId, confId);
        if (baseMapper.update(null, updateWrapper) > 0) {
            log.info("Conference status updated, conferenceId={}, from={}, to={}", confId, oldStatus, newStatus);
        }
    }

    @Override
    public ConferenceRespDTO getConferenceByShortName(String shortName) {
        LambdaQueryWrapper<ConferenceDO> queryWrapper = Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getShortName, shortName)
                .eq(ConferenceDO::getDelFlag, 0);
        ConferenceDO conferenceDO = baseMapper.selectOne(queryWrapper);
        if (conferenceDO == null) {
            throw new ServiceException("会议不存在");
        }

        LambdaQueryWrapper<ConfLocalDO> localWrapper = Wrappers.lambdaQuery(ConfLocalDO.class)
                .eq(ConfLocalDO::getConferId, conferenceDO.getId())
                .eq(ConfLocalDO::getDelFlag, 0);
        ConfLocalDO confLocalDO = confLocalMapper.selectOne(localWrapper);

        ConferenceRespDTO respDTO = BeanUtil.toBean(conferenceDO, ConferenceRespDTO.class);
        if (confLocalDO != null) {
            respDTO.setProvince(confLocalDO.getProvince());
            respDTO.setCity(confLocalDO.getCity());
            respDTO.setCountry(confLocalDO.getCountry());
            respDTO.setAddress(confLocalDO.getAddress());
        }
        return respDTO;
    }

    private boolean importantDatesCompleted(ConferenceDO conference) {
        return conference.getPaperSubmissionDeadline() != null
                && conference.getNotificationOfAcceptance() != null
                && conference.getCameraReadySubmission() != null
                && conference.getEarlyBirdRegistration() != null
                && conference.getConferenceStartDate() != null
                && conference.getConferenceEndDate() != null;
    }

    private void validateImportantDates(ConferenceImportantDatesReqDTO requestParam) {
        if (requestParam == null
                || requestParam.getPaperSubmissionDeadline() == null
                || requestParam.getNotificationOfAcceptance() == null
                || requestParam.getCameraReadySubmission() == null
                || requestParam.getEarlyBirdRegistration() == null
                || requestParam.getConferenceStartDate() == null
                || requestParam.getConferenceEndDate() == null) {
            throw new ClientException("All important dates are required");
        }
        if (!requestParam.getPaperSubmissionDeadline().before(requestParam.getNotificationOfAcceptance())) {
            throw new ClientException("Paper Submission Deadline must be earlier than Notification of Acceptance");
        }
        if (!requestParam.getNotificationOfAcceptance().before(requestParam.getCameraReadySubmission())) {
            throw new ClientException("Notification of Acceptance must be earlier than Camera-ready Submission");
        }
        if (!requestParam.getCameraReadySubmission().before(requestParam.getConferenceStartDate())) {
            throw new ClientException("Camera-ready Submission must be earlier than Conference Start Date");
        }
        if (requestParam.getEarlyBirdRegistration().after(requestParam.getConferenceStartDate())) {
            throw new ClientException("Early-bird Registration must be no later than Conference Start Date");
        }
        if (requestParam.getConferenceStartDate().after(requestParam.getConferenceEndDate())) {
            throw new ClientException("Conference Start Date must be no later than Conference End Date");
        }
        if (Objects.equals(requestParam.getConferenceStartDate(), requestParam.getConferenceEndDate())
                || requestParam.getConferenceStartDate().before(requestParam.getConferenceEndDate())) {
            return;
        }
        throw new ClientException("Invalid conference date range");
    }

    @Override
    public IPage<ConferencePageRespDTO> pageConference(ConferencePageRepDTO requestParam) {
        return baseMapper.pageConference(requestParam);
    }

    @Transactional
    @Override
    public void deleteConference(String shortName) {
        LambdaQueryWrapper<ConferenceDO> queryWrapper = Wrappers.lambdaQuery(ConferenceDO.class)
                .eq(ConferenceDO::getShortName, shortName)
                .eq(ConferenceDO::getDelFlag, 0);
        ConferenceDO conferenceDO = baseMapper.selectOne(queryWrapper);
        if (conferenceDO == null) {
            throw new ServiceException("会议不存在");
        }

        conferencePermissionService.requireAtLeastRole(conferenceDO.getId(), ConferenceRole.COMMITTEE);

        Long confId = conferenceDO.getId();

        List<Long> taskLogIds = sysTaskLogMapper.selectList(
            Wrappers.lambdaQuery(SysTaskLogDO.class)
                .select(SysTaskLogDO::getId)
                .eq(SysTaskLogDO::getConfereId, confId)
        ).stream().map(SysTaskLogDO::getId).toList();

        if (!taskLogIds.isEmpty()) {
            sysMailLogMapper.delete(Wrappers.lambdaQuery(SysMailLogDO.class)
                .in(SysMailLogDO::getTaskLogId, taskLogIds));
            sysTaskBatchStatMapper.delete(Wrappers.lambdaQuery(SysTaskBatchStatDO.class)
                .in(SysTaskBatchStatDO::getTaskLogId, taskLogIds));
        }

        confEmailContentMapper.delete(Wrappers.lambdaQuery(ConfEmailContentDO.class)
            .eq(ConfEmailContentDO::getConfId, confId));
        sysTaskLogMapper.delete(Wrappers.lambdaQuery(SysTaskLogDO.class)
            .eq(SysTaskLogDO::getConfereId, confId));

        mailSendLogMapper.delete(Wrappers.lambdaQuery(MailSendLogDO.class)
            .eq(MailSendLogDO::getConferenceId, confId));
        mailSendTaskMapper.delete(Wrappers.lambdaQuery(MailSendTaskDO.class)
            .eq(MailSendTaskDO::getConferenceId, confId));
        mailTemplateMapper.delete(Wrappers.lambdaQuery(MailTemplateDO.class)
            .eq(MailTemplateDO::getConferenceId, confId));
        conferenceMailAccountMapper.delete(Wrappers.lambdaQuery(ConferenceMailAccountDO.class)
            .eq(ConferenceMailAccountDO::getConferenceId, confId));

        confStageMapper.delete(Wrappers.lambdaQuery(ConfStageDO.class)
            .eq(ConfStageDO::getConferenceId, confId));
        confMemberMapper.delete(Wrappers.lambdaQuery(ConfMemberDO.class)
            .eq(ConfMemberDO::getConfId, confId));
        confCommitteeMapper.delete(Wrappers.lambdaQuery(ConfCommitteeDO.class)
            .eq(ConfCommitteeDO::getConfId, confId));
        confMilestoneMapper.delete(Wrappers.lambdaQuery(ConfMilestoneDO.class)
            .eq(ConfMilestoneDO::getConfereId, confId));
        confLocalMapper.delete(Wrappers.lambdaQuery(ConfLocalDO.class)
            .eq(ConfLocalDO::getConferId, confId));

        baseMapper.deleteById(confId);
        evictStageCacheAfterCommit(confId);
        conferenceTaskService.evictGeneratedTaskCacheAfterCommit(confId);
        log.info("Conference deleted permanently, shortName={}, operator={}", shortName, UserContext.getUsername());
    }

    @EventListener
    public void handleConferenceStatusChange(ConferenceStatusChangeEvent event) {
        try {
            updateStatus(event.getConfId(), event.getTargetStatus());
        } catch (Exception e) {
            log.error("Failed to handle conference status change, conferenceId={}, targetStatus={}",
                    event.getConfId(), event.getTargetStatus().getStatus(), e);
        }
    }

    private void validateRequiredBasicInfo(ConferenceUpdateRepDTO requestParam) {
        if (!StringUtils.hasText(requestParam.getContactEmail())) {
            throw new ClientException("联系人邮箱不能为空");
        }
        if (!requestParam.getContactEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ClientException("联系人邮箱格式不正确");
        }
        if (!StringUtils.hasText(requestParam.getWebsiteUrl())) {
            throw new ClientException("会议网站不能为空");
        }
        if (!StringUtils.hasText(requestParam.getHost())) {
            throw new ClientException("主办方不能为空");
        }
    }

    private void evictStageCacheAfterCommit(Long conferenceId) {
        Runnable evict = () -> {
            try {
                stringRedisTemplate.delete(RedisCacheConstant.CONFERENCE_STAGE_LIST_KEY + conferenceId);
            } catch (Exception ex) {
                log.warn("Delete conference stage cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
            return;
        }
        evict.run();
    }
}
