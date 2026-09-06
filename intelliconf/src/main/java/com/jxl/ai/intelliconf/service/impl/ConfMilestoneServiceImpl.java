package com.jxl.ai.intelliconf.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskInstanceDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskInstanceMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.req.MilestoneUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneListRespDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneRespDTO;
import com.jxl.ai.intelliconf.event.MilestoneActiveEvent;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.ConfMilestoneService;
import com.jxl.ai.intelliconf.service.ConferenceTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.jxl.ai.intelliconf.common.constant.MilestoneConstant.*;

/**
 * 会议里程碑接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfMilestoneServiceImpl extends ServiceImpl<ConfMilestoneMapper, ConfMilestoneDO> implements ConfMilestoneService {

    private final ConferenceMapper conferenceMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ConferenceTimelineService conferenceTimelineService;
    private final ConfTaskInstanceMapper confTaskInstanceMapper;

    @Override
    public List<ConfMilestoneDO> listByConferenceId(Long confereId) {
        log.info("listByConferenceId 被调用，参数 confereId: {}", confereId);
        LambdaQueryWrapper<ConfMilestoneDO> queryWrapper = Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, confereId)
                .orderByAsc(ConfMilestoneDO::getStartDate);
        List<ConfMilestoneDO> result = baseMapper.selectList(queryWrapper);
        log.info("listByConferenceId 查询结果数量: {}", result.size());
        return result;
    }

    @Override
    public ConfMilestoneDO getByConferenceIdAndNodeCode(Long confereId, String nodeCode) {
        LambdaQueryWrapper<ConfMilestoneDO> queryWrapper = Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, confereId)
                .eq(ConfMilestoneDO::getNodeCode, nodeCode);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public MilestoneListRespDTO getMilestoneList(Long confereId) {
        log.info("查询里程碑列表，会议ID: {}", confereId);
        List<ConfMilestoneDO> milestones = listByConferenceId(confereId);
        log.info("查询到的里程碑数量: {}", milestones.size());
        if (milestones.isEmpty()) {
            log.warn("会议ID {} 没有找到任何里程碑数据", confereId);
        }
        List<MilestoneRespDTO> respList = milestones.stream()
                .map(this::toMilestoneResp)
                .collect(Collectors.toList());
        boolean allConfirmed = !respList.isEmpty()
                && respList.stream().allMatch(dto -> CONFIRM_YES.equals(dto.getIsConfirmed()));
        return new MilestoneListRespDTO(allConfirmed, respList);
    }

    @Override
    public MilestoneListRespDTO getMilestoneListByShortName(String shortName) {
        log.info("根据会议简称/ID查询里程碑列表，shortName: {}", shortName);
        
        Long conferenceId = findConferenceId(shortName);
        if (conferenceId == null) {
            throw new ServiceException("会议不存在: " + shortName);
        }
        
        // 调用已有方法查询里程碑
        return getMilestoneList(conferenceId);
    }

    private MilestoneRespDTO toMilestoneResp(ConfMilestoneDO milestone) {
        MilestoneRespDTO resp = BeanUtil.toBean(milestone, MilestoneRespDTO.class);
        List<ConfTaskInstanceDO> tasks = confTaskInstanceMapper.selectList(
                Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                        .eq(ConfTaskInstanceDO::getMilestoneId, milestone.getId())
        );
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int progressPercent = total > 0 ? Math.round(completed * 100f / total) : (STATUS_COMPLETED.equals(milestone.getStatus()) ? 100 : 0);
        resp.setTaskTotalCount(total);
        resp.setTaskCompletedCount(completed);
        resp.setTaskPendingCount((int) tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());
        resp.setTaskProcessingCount((int) tasks.stream().filter(t -> "PROCESSING".equals(t.getStatus())).count());
        resp.setTaskOverdueCount((int) tasks.stream().filter(t -> "OVERDUE".equals(t.getStatus())).count());
        resp.setCurrent(STATUS_IN_PROGRESS.equals(milestone.getStatus()));
        resp.setTaskProgressPercent(progressPercent);
        resp.setTaskProgressText(completed + "/" + total);
        return resp;
    }

    /**
     * 智能查找会议ID：支持数字ID或shortName
     * 
     * @param idOrShortName 会议ID或简称
     * @return 会议ID，找不到返回 null
     */
    private Long findConferenceId(String idOrShortName) {
        if (idOrShortName == null || idOrShortName.trim().isEmpty()) {
            return null;
        }
        
        // 判断是否为纯数字（会议ID）
        if (idOrShortName.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(idOrShortName);
                // 按 ID 查询
                ConferenceDO conferenceById = conferenceMapper.selectOne(
                        Wrappers.lambdaQuery(ConferenceDO.class)
                                .eq(ConferenceDO::getId, id)
                                .eq(ConferenceDO::getDelFlag, 0)
                );
                if (conferenceById != null) {
                    log.debug("按ID查询到会议: id={}, shortName={}", id, conferenceById.getShortName());
                    return conferenceById.getId();
                }
            } catch (NumberFormatException e) {
                log.warn("解析会议ID失败: {}", idOrShortName, e);
            }
        }
        
        // 按 shortName 查询
        ConferenceDO conferenceByName = conferenceMapper.selectOne(
                Wrappers.lambdaQuery(ConferenceDO.class)
                        .eq(ConferenceDO::getShortName, idOrShortName)
                        .eq(ConferenceDO::getDelFlag, 0)
        );
        if (conferenceByName != null) {
            log.debug("按shortName查询到会议: shortName={}, id={}", idOrShortName, conferenceByName.getId());
            return conferenceByName.getId();
        }
        
        log.warn("未找到会议: {}", idOrShortName);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmAndPublish(Long conferenceId) {
        conferencePermissionService.requireAtLeastRole(conferenceId, ConferenceRole.COMMITTEE);

        // 1. 查询该会议所有里程碑
        List<ConfMilestoneDO> milestones = listByConferenceId(conferenceId);
        if (milestones.isEmpty()) {
            throw new ServiceException("该会议暂无里程碑节点，无法确认时间轴");
        }

        // 2. 校验所有节点的 start_date 和 target_end_date 不为空
        List<String> invalidNodes = milestones.stream()
                .filter(m -> m.getStartDate() == null || m.getTargetEndDate() == null)
                .map(ConfMilestoneDO::getNodeName)
                .collect(Collectors.toList());
        if (!invalidNodes.isEmpty()) {
            throw new ServiceException("以下节点时间未填写，无法确认时间轴：" + String.join("、", invalidNodes));
        }

        // 3. 检查是否已经确认过（幂等性检查）
        boolean alreadyConfirmed = milestones.stream()
                .allMatch(m -> CONFIRM_YES.equals(m.getIsConfirmed()));
        if (alreadyConfirmed) {
            log.info("会议ID: {} 的时间轴已经确认过，跳过重复确认", conferenceId);
            return;
        }

        // 4. 批量将所有里程碑 is_confirmed 更新为 1
        LambdaUpdateWrapper<ConfMilestoneDO> milestoneUpdate = new LambdaUpdateWrapper<ConfMilestoneDO>()
                .eq(ConfMilestoneDO::getConfereId, conferenceId)
                .set(ConfMilestoneDO::getIsConfirmed, CONFIRM_YES);
        baseMapper.update(null, milestoneUpdate);
        log.info("会议ID: {} 的时间轴已全部确认", conferenceId);

        // 5. 触发 INITIATION 阶段的启动
        activateInitiationMilestone(conferenceId, milestones);
    }

    /**
     * 激活 INITIATION 里程碑
     * 将其状态更新为"进行中"，发布事件并触发任务分发
     */
    private void activateInitiationMilestone(Long conferenceId, List<ConfMilestoneDO> milestones) {
        // 查找 INITIATION 阶段的里程碑
        ConfMilestoneDO initiationMilestone = milestones.stream()
                .filter(m -> NODE_CODE_INITIATION.equals(m.getNodeCode()))
                .findFirst()
                .orElse(null);

        if (initiationMilestone == null) {
            log.warn("未找到 INITIATION 里程碑节点: confId={}", conferenceId);
            return;
        }

        if (!STATUS_WAITING.equals(initiationMilestone.getStatus())) {
            log.info("INITIATION 里程碑状态非等待中，跳过激活: confId={}, status={}",
                    conferenceId, initiationMilestone.getStatus());
            return;
        }

        // 更新 INITIATION 状态为"进行中"（使用乐观锁）
        LambdaUpdateWrapper<ConfMilestoneDO> activateUpdate = new LambdaUpdateWrapper<ConfMilestoneDO>()
                .eq(ConfMilestoneDO::getId, initiationMilestone.getId())
                .eq(ConfMilestoneDO::getStatus, STATUS_WAITING)
                .set(ConfMilestoneDO::getStatus, STATUS_IN_PROGRESS);
        int updated = baseMapper.update(null, activateUpdate);

        if (updated == 0) {
            log.warn("INITIATION 里程碑激活失败，可能已被其他线程激活: confId={}", conferenceId);
            return;
        }

        log.info("INITIATION 阶段已激活: confId={}, milestoneId={}",
                conferenceId, initiationMilestone.getId());

        // 发布激活事件
        eventPublisher.publishEvent(
                new MilestoneActiveEvent(this, conferenceId, NODE_CODE_INITIATION)
        );

        // 发布确认事件，触发任务分发（解耦依赖）
        conferenceTimelineService.generateTasksForMilestone(conferenceId, initiationMilestone.getId(), "SYSTEM");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmAndPublishByShortName(String shortName) {
        log.info("根据会议简称/ID确认并发布时间轴，shortName: {}", shortName);
        
        Long conferenceId = findConferenceId(shortName);
        if (conferenceId == null) {
            throw new ServiceException("会议不存在: " + shortName);
        }
        
        // 调用已有方法确认并发布
        confirmAndPublish(conferenceId);
    }

    @Override
    public MilestoneRespDTO getActiveMilestone(Long confId) {
        // 优先级 1：查找 status = 1（处理中）的节点
        LambdaQueryWrapper<ConfMilestoneDO> inProgressWrapper = Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, confId)
                .eq(ConfMilestoneDO::getStatus, STATUS_IN_PROGRESS)
                .last("LIMIT 1");
        ConfMilestoneDO inProgress = baseMapper.selectOne(inProgressWrapper);
        if (inProgress != null) {
            return BeanUtil.toBean(inProgress, MilestoneRespDTO.class);
        }

        // 优先级 2：查找 status = 0（等待中）且 start_date 最早的节点
        LambdaQueryWrapper<ConfMilestoneDO> upcomingWrapper = Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, confId)
                .eq(ConfMilestoneDO::getStatus, STATUS_WAITING)
                .orderByAsc(ConfMilestoneDO::getStartDate)
                .last("LIMIT 1");
        ConfMilestoneDO upcoming = baseMapper.selectOne(upcomingWrapper);
        if (upcoming != null) {
            return BeanUtil.toBean(upcoming, MilestoneRespDTO.class);
        }

        // 优先级 3：所有节点均已达成/跳过，返回 target_end_date 最晚的最后一个节点
        LambdaQueryWrapper<ConfMilestoneDO> lastWrapper = Wrappers.lambdaQuery(ConfMilestoneDO.class)
                .eq(ConfMilestoneDO::getConfereId, confId)
                .orderByDesc(ConfMilestoneDO::getTargetEndDate)
                .last("LIMIT 1");
        ConfMilestoneDO last = baseMapper.selectOne(lastWrapper);
        if (last == null) {
            throw new ServiceException("该会议暂无里程碑节点");
        }
        return BeanUtil.toBean(last, MilestoneRespDTO.class);
    }

    @Override
    public void updateMilestone(Long id, MilestoneUpdateReqDTO requestParam) {
        // 1. 查询当前节点，获取 is_confirmed 状态
        ConfMilestoneDO existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new ClientException("里程碑节点不存在");
        }

        conferencePermissionService.requireAtLeastRole(existing.getConfereId(), ConferenceRole.COMMITTEE);

        // 2. 计划时间锁定校验：已确认后禁止修改 start_date / target_end_date
        boolean tryModifyPlanDates = requestParam.getStartDate() != null || requestParam.getTargetEndDate() != null;
        if (CONFIRM_YES.equals(existing.getIsConfirmed()) && tryModifyPlanDates) {
            throw new ServiceException("时间轴已确认锁定，禁止直接修改");
        }

        // 3. 构建更新条件（未确认时允许修改计划时间；确认后仅允许更新执行数据）
        LambdaUpdateWrapper<ConfMilestoneDO> updateWrapper = new LambdaUpdateWrapper<ConfMilestoneDO>()
                .eq(ConfMilestoneDO::getId, id)
                .set(requestParam.getStartDate() != null, ConfMilestoneDO::getStartDate, requestParam.getStartDate())
                .set(requestParam.getTargetEndDate() != null, ConfMilestoneDO::getTargetEndDate, requestParam.getTargetEndDate())
                .set(requestParam.getActualEndDate() != null, ConfMilestoneDO::getActualEndDate, requestParam.getActualEndDate())
                .set(requestParam.getStatus() != null, ConfMilestoneDO::getStatus, requestParam.getStatus())
                .set(requestParam.getRemark() != null, ConfMilestoneDO::getRemark, requestParam.getRemark());
        baseMapper.update(null, updateWrapper);
    }
}
