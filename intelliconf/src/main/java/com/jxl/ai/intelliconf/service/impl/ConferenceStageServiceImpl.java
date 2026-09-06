package com.jxl.ai.intelliconf.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.constant.ConferenceSetupConstant;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDO;
import com.jxl.ai.intelliconf.dao.entity.ConfStageDefDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageDefMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfStageMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.req.ConferenceStageGenerateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceStageRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConferenceService;
import com.jxl.ai.intelliconf.service.ConferenceStageService;
import com.jxl.ai.intelliconf.service.ConferenceTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jxl.ai.intelliconf.common.constant.RedisCacheConstant.CONFERENCE_STAGE_LIST_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenceStageServiceImpl implements ConferenceStageService {

    private static final int REQUIRED_STAGE_COUNT = 7;
    private static final long STAGE_CACHE_TTL_MINUTES = 30L;

    private final ConferenceService conferenceService;
    private final ConferencePermissionService conferencePermissionService;
    private final ConferenceMapper conferenceMapper;
    private final ConfStageDefMapper confStageDefMapper;
    private final ConfStageMapper confStageMapper;
    private final ConferenceTaskService conferenceTaskService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ConferenceStageRespDTO> generateStagePreview(String conferenceKey) {
        ConferenceDO conference = prepareConference(conferenceKey);
        List<ConfStageDefDO> definitions = loadStageDefinitions();
        List<ConferenceStageRespDTO> preview = buildPreview(conference, definitions);
        log.info("Conference stage preview generated, conferenceId={}, operator={}",
                conference.getId(), UserContext.getUsername());
        return preview;
    }

    @Transactional
    @Override
    public List<ConferenceStageRespDTO> confirmGenerateStages(String conferenceKey, List<ConferenceStageGenerateReqDTO> requestParam) {
        ConferenceDO conference = prepareConference(conferenceKey);
        List<ConfStageDefDO> definitions = loadStageDefinitions();
        Map<String, ConfStageDefDO> defMap = definitions.stream()
                .collect(Collectors.toMap(ConfStageDefDO::getStageCode, Function.identity()));
        validateGenerateRequest(requestParam, defMap);

        List<ConfStageDO> existingStages = confStageMapper.selectList(Wrappers.lambdaQuery(ConfStageDO.class)
                .eq(ConfStageDO::getConferenceId, conference.getId()));
        Map<String, ConfStageDO> existingMap = existingStages.stream()
                .collect(Collectors.toMap(ConfStageDO::getStageCode, Function.identity(), (left, right) -> left));
        for (ConfStageDO existingStage : existingStages) {
            ConfStageDO tempOrder = new ConfStageDO();
            tempOrder.setStageOrder(-existingStage.getId().intValue());
            confStageMapper.update(tempOrder, Wrappers.lambdaUpdate(ConfStageDO.class)
                    .eq(ConfStageDO::getId, existingStage.getId()));
        }

        List<ConfStageDO> savedStages = new ArrayList<>();
        List<ConferenceStageGenerateReqDTO> sortedRequest = requestParam.stream()
                .sorted(Comparator.comparing(ConferenceStageGenerateReqDTO::getStageOrder))
                .toList();
        Date now = new Date();
        for (int i = 0; i < sortedRequest.size(); i++) {
            ConferenceStageGenerateReqDTO item = sortedRequest.get(i);
            boolean first = i == 0;
            ConfStageDO stage = buildStageDO(conference.getId(), item, first);
            stage.setUpdateTime(now);
            ConfStageDO existing = existingMap.get(item.getStageCode());
            if (existing == null) {
                stage.setCreateTime(now);
                confStageMapper.insert(stage);
                savedStages.add(stage);
            } else {
                stage.setId(existing.getId());
                LambdaUpdateWrapper<ConfStageDO> updateWrapper = Wrappers.lambdaUpdate(ConfStageDO.class)
                        .eq(ConfStageDO::getConferenceId, conference.getId())
                        .eq(ConfStageDO::getStageCode, item.getStageCode());
                confStageMapper.update(stage, updateWrapper);
                savedStages.add(confStageMapper.selectById(existing.getId()));
            }
        }

        ConferenceDO update = new ConferenceDO();
        update.setSetupStatus(ConferenceSetupConstant.STAGE_GENERATED);
        conferenceMapper.update(update, Wrappers.lambdaUpdate(ConferenceDO.class)
                .eq(ConferenceDO::getId, conference.getId()));
        evictStageCacheAfterCommit(conference.getId());
        conferenceTaskService.evictGeneratedTaskCacheAfterCommit(conference.getId());
        log.info("Conference stages generated, conferenceId={}, count={}, operator={}",
                conference.getId(), savedStages.size(), UserContext.getUsername());
        return savedStages.stream()
                .sorted(Comparator.comparing(ConfStageDO::getStageOrder))
                .map(this::toResp)
                .toList();
    }

    @Override
    public List<ConferenceStageRespDTO> listStages(String conferenceKey) {
        ConferenceDO conference = conferenceService.resolveConference(conferenceKey);
        conferencePermissionService.requireAtLeastRole(conference.getId(), ConferenceRole.COMMITTEE);
        List<ConferenceStageRespDTO> cachedStages = getStagesFromCache(conference.getId());
        if (cachedStages != null) {
            return cachedStages;
        }

        List<ConferenceStageRespDTO> stages = confStageMapper.selectList(Wrappers.lambdaQuery(ConfStageDO.class)
                        .eq(ConfStageDO::getConferenceId, conference.getId())
                        .orderByAsc(ConfStageDO::getStageOrder))
                .stream()
                .map(this::toResp)
                .toList();
        putStagesToCache(conference.getId(), stages);
        return stages;
    }

    private ConferenceDO prepareConference(String conferenceKey) {
        ConferenceDO conference = conferenceService.resolveConference(conferenceKey);
        conferencePermissionService.requireAtLeastRole(conference.getId(), ConferenceRole.COMMITTEE);
        if (!importantDatesCompleted(conference)) {
            throw new ClientException("Important dates must be completed before generating stages");
        }
        return conference;
    }

    private List<ConfStageDefDO> loadStageDefinitions() {
        List<ConfStageDefDO> definitions = confStageDefMapper.selectList(Wrappers.lambdaQuery(ConfStageDefDO.class)
                .eq(ConfStageDefDO::getStatus, 1)
                .orderByAsc(ConfStageDefDO::getStageOrder));
        if (definitions.size() != REQUIRED_STAGE_COUNT) {
            throw new ServiceException("Stage template configuration must contain exactly seven enabled stages");
        }
        Set<String> stageCodes = definitions.stream().map(ConfStageDefDO::getStageCode).collect(Collectors.toSet());
        for (String requiredCode : requiredStageCodes()) {
            if (!stageCodes.contains(requiredCode)) {
                throw new ServiceException("Stage template missing: " + requiredCode);
            }
        }
        return definitions;
    }

    private List<ConferenceStageRespDTO> buildPreview(ConferenceDO conference,
                                                       List<ConfStageDefDO> definitions) {
        Date created = conference.getCreateTime() == null ? new Date() : conference.getCreateTime();
        Date paperDeadline = conference.getPaperSubmissionDeadline();
        Date notification = conference.getNotificationOfAcceptance();
        Date earlyBird = conference.getEarlyBirdRegistration();
        Date start = conference.getConferenceStartDate();
        Date end = conference.getConferenceEndDate();

        Map<String, Date[]> periods = new HashMap<>();
        Date startupEndCandidate = plusDays(created, 7);
        Date paperPreviousDay = plusDays(paperDeadline, -1);
        Date startupEnd = startupEndCandidate.before(paperPreviousDay) ? startupEndCandidate : paperPreviousDay;
        periods.put("CONFERENCE_STARTUP", new Date[]{created, startupEnd});
        periods.put("CALL_FOR_PAPERS", new Date[]{plusDays(startupEnd, 1), paperDeadline});
        periods.put("REVIEW", new Date[]{plusDays(paperDeadline, 1), notification});
        periods.put("REGISTRATION", new Date[]{notification, earlyBird});
        periods.put("CONFERENCE_PREPARATION", new Date[]{notification, plusDays(start, -1)});
        periods.put("CONFERENCE_DAYS", new Date[]{start, end});
        periods.put("POST_CONFERENCE", new Date[]{plusDays(end, 1), plusDays(end, 60)});

        return definitions.stream()
                .map(definition -> {
                    Date[] period = periods.get(definition.getStageCode());
                    if (period == null) {
                        throw new ServiceException("Unsupported stage template: " + definition.getStageCode());
                    }
                    return ConferenceStageRespDTO.builder()
                            .stageDefId(definition.getId())
                            .stageCode(definition.getStageCode())
                            .stageName(definition.getStageName())
                            .stageOrder(definition.getStageOrder())
                            .plannedStartTime(period[0])
                            .plannedEndTime(period[1])
                            .stageStatus(definition.getStageOrder() != null && definition.getStageOrder() == 1
                                    ? ConferenceSetupConstant.IN_PROGRESS
                                    : ConferenceSetupConstant.PLANNED)
                            .progress(BigDecimal.ZERO)
                            .isCurrent(definition.getStageOrder() != null && definition.getStageOrder() == 1 ? 1 : 0)
                            .build();
                })
                .sorted(Comparator.comparing(ConferenceStageRespDTO::getStageOrder))
                .toList();
    }

    private void validateGenerateRequest(List<ConferenceStageGenerateReqDTO> requestParam, Map<String, ConfStageDefDO> defMap) {
        if (requestParam == null || requestParam.size() != REQUIRED_STAGE_COUNT) {
            throw new ClientException("Exactly seven stages are required");
        }
        Set<String> stageCodes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ConferenceStageGenerateReqDTO item : requestParam) {
            if (item == null || !StringUtils.hasText(item.getStageCode()) || !StringUtils.hasText(item.getStageName())) {
                throw new ClientException("Stage code and name are required");
            }
            ConfStageDefDO definition = defMap.get(item.getStageCode());
            if (definition == null || !definition.getId().equals(item.getStageDefId())) {
                throw new ClientException("Stage cannot be matched to an enabled template: " + item.getStageCode());
            }
            if (!stageCodes.add(item.getStageCode())) {
                throw new ClientException("Duplicate stage code: " + item.getStageCode());
            }
            if (item.getStageOrder() == null || !orders.add(item.getStageOrder())) {
                throw new ClientException("Stage order must be present and unique");
            }
            if (item.getPlannedStartTime() == null || item.getPlannedEndTime() == null) {
                throw new ClientException("Stage planned time is required");
            }
            if (item.getPlannedStartTime().after(item.getPlannedEndTime())) {
                throw new ClientException("Stage planned start time cannot be later than end time");
            }
        }
    }

    private ConfStageDO buildStageDO(Long conferenceId, ConferenceStageGenerateReqDTO item, boolean first) {
        return ConfStageDO.builder()
                .conferenceId(conferenceId)
                .stageDefId(item.getStageDefId())
                .stageCode(item.getStageCode())
                .stageName(item.getStageName())
                .stageOrder(item.getStageOrder())
                .plannedStartTime(item.getPlannedStartTime())
                .plannedEndTime(item.getPlannedEndTime())
                .stageStatus(first ? ConferenceSetupConstant.IN_PROGRESS : ConferenceSetupConstant.PLANNED)
                .progress(BigDecimal.ZERO)
                .isCurrent(first ? 1 : 0)
                .build();
    }

    private ConferenceStageRespDTO toResp(ConfStageDO stage) {
        return ConferenceStageRespDTO.builder()
                .id(stage.getId())
                .stageDefId(stage.getStageDefId())
                .stageCode(stage.getStageCode())
                .stageName(stage.getStageName())
                .stageOrder(stage.getStageOrder())
                .plannedStartTime(stage.getPlannedStartTime())
                .plannedEndTime(stage.getPlannedEndTime())
                .stageStatus(stage.getStageStatus())
                .progress(stage.getProgress())
                .isCurrent(stage.getIsCurrent())
                .build();
    }

    private boolean importantDatesCompleted(ConferenceDO conference) {
        return conference.getPaperSubmissionDeadline() != null
                && conference.getNotificationOfAcceptance() != null
                && conference.getCameraReadySubmission() != null
                && conference.getEarlyBirdRegistration() != null
                && conference.getConferenceStartDate() != null
                && conference.getConferenceEndDate() != null;
    }

    private Date plusDays(Date date, int days) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).plusDays(days);
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private List<String> requiredStageCodes() {
        return List.of(
                "CONFERENCE_STARTUP",
                "CALL_FOR_PAPERS",
                "REVIEW",
                "REGISTRATION",
                "CONFERENCE_PREPARATION",
                "CONFERENCE_DAYS",
                "POST_CONFERENCE"
        );
    }

    private List<ConferenceStageRespDTO> getStagesFromCache(Long conferenceId) {
        String cacheKey = buildStageCacheKey(conferenceId);
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedValue)) {
                return null;
            }
            return JSON.parseObject(cachedValue, new TypeReference<List<ConferenceStageRespDTO>>() {
            });
        } catch (Exception ex) {
            log.warn("Read conference stage cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void putStagesToCache(Long conferenceId, List<ConferenceStageRespDTO> stages) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildStageCacheKey(conferenceId),
                    JSON.toJSONString(stages),
                    STAGE_CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception ex) {
            log.warn("Write conference stage cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
        }
    }

    private void evictStageCacheAfterCommit(Long conferenceId) {
        Runnable evict = () -> evictStageCache(conferenceId);
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

    private void evictStageCache(Long conferenceId) {
        try {
            stringRedisTemplate.delete(buildStageCacheKey(conferenceId));
        } catch (Exception ex) {
            log.warn("Delete conference stage cache failed, conferenceId={}, message={}", conferenceId, ex.getMessage());
        }
    }

    private String buildStageCacheKey(Long conferenceId) {
        return CONFERENCE_STAGE_LIST_KEY + conferenceId;
    }
}
