package com.jxl.ai.intelliconf.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.config.DeepSeekProperties;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskDO;
import com.jxl.ai.intelliconf.dao.entity.ConfTaskMailPlanDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.entity.MailTemplateDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMailPlanMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfTaskMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dao.mapper.MailTemplateMapper;
import com.jxl.ai.intelliconf.mail.infrastructure.DeepSeekClient;
import com.jxl.ai.intelliconf.mail.infrastructure.TemplateRenderService;
import com.jxl.ai.intelliconf.dto.req.TaskMailPlanApproveReqDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.dto.resp.TaskMailPlanRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskMailAutomationService {

    private static final int GENERATE_AHEAD_DAYS = 7;

    private final ConfTaskMailPlanMapper planMapper;
    private final ConfTaskMapper taskMapper;
    private final ConferenceMapper conferenceMapper;
    private final MailTemplateMapper templateMapper;
    private final TemplateRenderService templateRenderService;
    private final MemberService memberService;
    private final BasicMailSendService basicMailSendService;
    private final ConferenceMemberRoleService memberRoleService;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties deepSeekProperties;

    @Transactional
    public int generateUpcomingPlans() {
        Date horizon = addDays(new Date(), GENERATE_AHEAD_DAYS);
        List<ConfTaskDO> tasks = taskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getTaskType, "EMAIL")
                .notIn(ConfTaskDO::getTaskStatus, List.of("COMPLETED", "CANCELLED"))
                .le(ConfTaskDO::getPlannedStartTime, horizon));
        int generated = 0;
        for (ConfTaskDO task : tasks) {
            if (createPlanIfMissing(task)) generated++;
        }
        refreshSystemDrafts();
        return generated;
    }

    @Transactional
    public List<TaskMailPlanRespDTO> listPlans(Long conferenceId) {
        memberRoleService.requireMember(conferenceId, currentUserId());
        List<ConfTaskDO> emailTasks = taskMapper.selectList(Wrappers.lambdaQuery(ConfTaskDO.class)
                .eq(ConfTaskDO::getConferenceId, conferenceId)
                .eq(ConfTaskDO::getTaskType, "EMAIL")
                .orderByAsc(ConfTaskDO::getPlannedStartTime));
        for (ConfTaskDO task : emailTasks) createPlanIfMissingWhenNear(task);
        return planMapper.selectList(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class)
                        .eq(ConfTaskMailPlanDO::getConferenceId, conferenceId)
                        .orderByAsc(ConfTaskMailPlanDO::getPlannedSendTime))
                .stream().map(this::toResp).toList();
    }

    @Transactional
    public TaskMailPlanRespDTO approve(Long conferenceId, Long planId, TaskMailPlanApproveReqDTO request) {
        ConfTaskMailPlanDO plan = requirePlan(conferenceId, planId);
        ConfTaskDO task = taskMapper.selectById(plan.getTaskId());
        requireApprover(conferenceId, task);
        if (request == null || !StringUtils.hasText(request.getSubject()) || !StringUtils.hasText(request.getContentBody())) {
            throw new ClientException("邮件主题和正文不能为空");
        }
        if ("SENT".equals(plan.getStatus()) || "SENDING".equals(plan.getStatus())) {
            throw new ClientException("邮件已发送或正在发送，不能重复审核");
        }
        if ("REJECTED".equals(plan.getStatus())) {
            throw new ClientException("该邮件已被拒绝，如需发送请重新生成或修改后再次审核");
        }
        Date now = new Date();
        plan.setSubject(request.getSubject().trim());
        plan.setContentBody(request.getContentBody().trim());
        plan.setPlannedSendTime(request.getPlannedSendTime() == null ? plan.getPlannedSendTime() : request.getPlannedSendTime());
        plan.setSelectedEmails(serializeEmails(request.getSelectedEmails()));
        plan.setStatus("APPROVED");
        plan.setApprovedBy(currentUserId());
        plan.setApprovedAt(now);
        plan.setErrorMessage(null);
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);
        return toResp(plan);
    }

    @Transactional
    public TaskMailPlanRespDTO reject(Long conferenceId, Long planId, String reason) {
        ConfTaskMailPlanDO plan = requirePlan(conferenceId, planId);
        ConfTaskDO task = taskMapper.selectById(plan.getTaskId());
        requireApprover(conferenceId, task);
        if ("SENT".equals(plan.getStatus()) || "SENDING".equals(plan.getStatus())) {
            throw new ClientException("邮件已发送或正在发送，不能拒绝");
        }
        Date now = new Date();
        plan.setStatus("REJECTED");
        plan.setApprovedBy(currentUserId());
        plan.setApprovedAt(now);
        plan.setErrorMessage(StringUtils.hasText(reason) ? ("审核拒绝：" + reason.trim()) : "审核拒绝");
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);
        return toResp(plan);
    }

    /** 待审核邮件数量（用于首页审核中心徽标；潜在投稿者审核不计入） */
    @Transactional(readOnly = true)
    public int countPendingReview(Long conferenceId) {
        Long count = planMapper.selectCount(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class)
                .eq(ConfTaskMailPlanDO::getConferenceId, conferenceId)
                .eq(ConfTaskMailPlanDO::getStatus, "PENDING_REVIEW"));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 重新生成邮件主题与正文。
     * <p>仅在草稿（PENDING_REVIEW）状态可用：重新走 resolveContent（数据库模板优先 → DeepSeek → 兜底），
     * 覆盖当前主题与正文；状态保持 PENDING_REVIEW，仍需组织者审核通过才会发送。
     */
    @Transactional
    public TaskMailPlanRespDTO regenerate(Long conferenceId, Long planId) {
        ConfTaskMailPlanDO plan = requirePlan(conferenceId, planId);
        ConfTaskDO task = taskMapper.selectById(plan.getTaskId());
        requireApprover(conferenceId, task);
        if (!"PENDING_REVIEW".equals(plan.getStatus())) {
            throw new ClientException("只有待审核状态的邮件可以重新生成");
        }
        ConferenceDO conference = conferenceMapper.selectById(plan.getConferenceId());
        MailContent content = resolveContent(conference, task, plan.getTargetRole());
        Date now = new Date();
        plan.setSubject(content.subject());
        plan.setContentBody(content.body());
        plan.setStatus("PENDING_REVIEW");
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);
        log.info("Regenerated mail plan, planId={}, taskCode={}, source via resolveContent",
                plan.getId(), task == null ? null : task.getTaskCode());
        return toResp(plan);
    }

    @Transactional
    public TaskMailPlanRespDTO retry(Long conferenceId, Long planId) {
        ConfTaskMailPlanDO plan = requirePlan(conferenceId, planId);
        ConfTaskDO task = taskMapper.selectById(plan.getTaskId());
        requireApprover(conferenceId, task);
        if (!"FAILED".equals(plan.getStatus())) throw new ClientException("只有发送失败的邮件可以重试");
        plan.setStatus("APPROVED");
        plan.setPlannedSendTime(new Date());
        plan.setErrorMessage(null);
        plan.setUpdatedAt(new Date());
        planMapper.updateById(plan);
        return toResp(plan);
    }

    @Transactional
    public void sendApprovedDuePlans() {
        List<ConfTaskMailPlanDO> plans = planMapper.selectList(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class)
                .eq(ConfTaskMailPlanDO::getStatus, "APPROVED")
                .le(ConfTaskMailPlanDO::getPlannedSendTime, new Date()));
        for (ConfTaskMailPlanDO plan : plans) sendOne(plan);
    }

    private boolean createPlanIfMissingWhenNear(ConfTaskDO task) {
        if (task.getPlannedStartTime() == null || task.getPlannedStartTime().after(addDays(new Date(), GENERATE_AHEAD_DAYS))) return false;
        return createPlanIfMissing(task);
    }

    private boolean createPlanIfMissing(ConfTaskDO task) {
        Long count = planMapper.selectCount(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class).eq(ConfTaskMailPlanDO::getTaskId, task.getId()));
        if (count != null && count > 0) return false;
        ConferenceDO conference = conferenceMapper.selectById(task.getConferenceId());
        String targetRole = resolveTargetRole(task.getTaskCode());
        List<ContactDTO> recipients = memberService.getParticipantsByRole(task.getConferenceId(), targetRole);
        Date now = new Date();
        String conferenceName = conference == null ? "本次会议" : conference.getTitle();
        MailContent content = resolveContent(conference, task, targetRole);
        planMapper.insert(ConfTaskMailPlanDO.builder()
                .conferenceId(task.getConferenceId())
                .taskId(task.getId())
                .targetRole(targetRole)
                .subject(content.subject())
                .contentBody(content.body())
                .plannedSendTime(task.getPlannedStartTime().before(now) ? now : task.getPlannedStartTime())
                .status("PENDING_REVIEW")
                .recipientCount(recipients == null ? 0 : recipients.size())
                .successCount(0).failCount(0)
                .generatedBy("SYSTEM").generatedAt(now).createdAt(now).updatedAt(now)
                .build());
        return true;
    }

    private void refreshSystemDrafts() {
        // 仅刷新有数据库模板的草稿（会议信息更新后正文跟着变）；
        // DeepSeek 生成的草稿首次创建后不再自动覆盖，避免每分钟重复调用浪费 token
        List<ConfTaskMailPlanDO> drafts = planMapper.selectList(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class)
                .eq(ConfTaskMailPlanDO::getStatus, "PENDING_REVIEW")
                .eq(ConfTaskMailPlanDO::getGeneratedBy, "SYSTEM"));
        for (ConfTaskMailPlanDO draft : drafts) {
            ConfTaskDO task = taskMapper.selectById(draft.getTaskId());
            ConferenceDO conference = task == null ? null : conferenceMapper.selectById(task.getConferenceId());
            if (task == null || conference == null) continue;
            MailTemplateDO template = templateMapper.selectOne(Wrappers.lambdaQuery(MailTemplateDO.class)
                    .eq(MailTemplateDO::getConferenceId, task.getConferenceId())
                    .eq(MailTemplateDO::getSceneCode, task.getTaskCode())
                    .eq(MailTemplateDO::getEnabled, true)
                    .orderByDesc(MailTemplateDO::getUpdatedAt)
                    .last("limit 1"));
            if (template == null) continue; // 无模板的草稿（DeepSeek 或兜底生成）不自动刷新
            Map<String, Object> variables = buildVariables(conference, draft.getTargetRole());
            String bodyTemplate = StringUtils.hasText(template.getHtmlTemplate()) ? template.getHtmlTemplate() : template.getTextTemplate();
            draft.setSubject(templateRenderService.render(template.getSubjectTemplate(), variables));
            draft.setContentBody(templateRenderService.render(bodyTemplate, variables));
            draft.setUpdatedAt(new Date());
            planMapper.updateById(draft);
        }
    }

    private MailContent resolveContent(ConferenceDO conference, ConfTaskDO task, String targetRole) {
        String conferenceName = conference == null ? "本次会议" : conference.getTitle();
        Map<String, Object> variables = buildVariables(conference, targetRole);
        MailTemplateDO template = templateMapper.selectOne(Wrappers.lambdaQuery(MailTemplateDO.class)
                .eq(MailTemplateDO::getConferenceId, task.getConferenceId())
                .eq(MailTemplateDO::getSceneCode, task.getTaskCode())
                .eq(MailTemplateDO::getEnabled, true)
                .orderByDesc(MailTemplateDO::getUpdatedAt)
                .last("limit 1"));
        if (template == null) {
            // 无数据库模板：优先调用 DeepSeek 生成；未配置或失败时降级到硬编码兜底文案
            if (deepSeekProperties.isEnabled() && StringUtils.hasText(deepSeekProperties.getApiKey())) {
                String scene = resolveSceneLabel(task.getTaskCode());
                String[] generated = deepSeekClient.generateMailContent(scene, variables);
                if (generated != null) {
                    log.info("DeepSeek generated mail content, taskCode={}, scene={}", task.getTaskCode(), scene);
                    return new MailContent(generated[0], generated[1]);
                }
                log.info("DeepSeek unavailable, fallback to built-in template, taskCode={}", task.getTaskCode());
            }
            return new MailContent(buildSubject(task.getTaskCode(), conferenceName), buildBody(task.getTaskCode(), conference));
        }
        String bodyTemplate = StringUtils.hasText(template.getHtmlTemplate()) ? template.getHtmlTemplate() : template.getTextTemplate();
        return new MailContent(
                templateRenderService.render(template.getSubjectTemplate(), variables),
                templateRenderService.render(bodyTemplate, variables)
        );
    }

    /** 构造模板渲染变量（数据库模板与 DeepSeek 共用） */
    private Map<String, Object> buildVariables(ConferenceDO conference, String targetRole) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("conferenceName", conference == null ? "本次会议" : conference.getTitle());
        variables.put("conferenceShortName", conference == null ? "" : conference.getShortName());
        variables.put("conferenceWebsite", conference == null ? "" : conference.getWebsiteUrl());
        variables.put("paperSubmissionDeadline", conference == null ? "" : conference.getPaperSubmissionDeadline());
        variables.put("notificationOfAcceptance", conference == null ? "" : conference.getNotificationOfAcceptance());
        variables.put("cameraReadySubmission", conference == null ? "" : conference.getCameraReadySubmission());
        variables.put("earlyBirdRegistration", conference == null ? "" : conference.getEarlyBirdRegistration());
        variables.put("conferenceStartDate", conference == null ? "" : conference.getConferenceStartDate());
        variables.put("conferenceEndDate", conference == null ? "" : conference.getConferenceEndDate());
        variables.put("targetRole", targetRole);
        return variables;
    }

    /** 把任务编码映射为中文场景描述，供 DeepSeek 理解邮件用途 */
    private String resolveSceneLabel(String taskCode) {
        if (taskCode == null) return "会议通用通知";
        if (taskCode.contains("REVIEW")) return "评审工作通知（提醒评审人按时完成论文评审）";
        if (taskCode.contains("FORMAT_REVISION")) return "论文格式修改通知（提醒作者按格式要求修改终稿）";
        if (taskCode.contains("DECISION")) return "论文评审结果通知（通知作者录用结果）";
        if (taskCode.contains("REGISTRATION")) return "注册缴费提醒（提醒已录用作者按时注册缴费）";
        if (taskCode.contains("ATTENDEE_GUIDE")) return "参会指南（向已注册参会者发送会议日程、签到、交通与会场安排）";
        if (taskCode.contains("THANK_YOU")) return "感谢参会通知（会议结束后向参会者致谢）";
        return "会议征稿与投稿提醒（邀请学者关注并投稿）";
    }

    private void sendOne(ConfTaskMailPlanDO plan) {
        plan.setStatus("SENDING");
        plan.setUpdatedAt(new Date());
        planMapper.updateById(plan);
        try {
            List<ContactDTO> fullList = memberService.getParticipantsByRole(plan.getConferenceId(), plan.getTargetRole());
            if (fullList == null || fullList.isEmpty()) throw new ClientException("没有找到符合发送角色的收件人");
            List<String> selected = parseEmails(plan.getSelectedEmails());
            List<ContactDTO> recipients = (selected == null || selected.isEmpty())
                    ? fullList
                    : fullList.stream().filter(c -> c.getEmail() != null && selected.contains(c.getEmail().trim().toLowerCase(java.util.Locale.ROOT))).toList();
            if (recipients.isEmpty()) throw new ClientException("审核勾选的收件人已不在该角色范围内，请重新审核");
            BasicMailSendRespDTO result = basicMailSendService.sendBatch(plan.getConferenceId(), null,
                    plan.getSubject(), plan.getContentBody(), recipients, false);
            int success = result.getSuccessCount() == null ? 0 : result.getSuccessCount();
            int failed = result.getFailCount() == null ? 0 : result.getFailCount();
            plan.setRecipientCount(recipients.size());
            plan.setSuccessCount(success);
            plan.setFailCount(failed);
            plan.setStatus(success > 0 ? "SENT" : "FAILED");
            plan.setSentAt(success > 0 ? new Date() : null);
            plan.setErrorMessage(success > 0 ? null : firstFailure(result));
            if (success > 0) completeTask(plan.getTaskId());
        } catch (Exception ex) {
            plan.setStatus("FAILED");
            plan.setErrorMessage(safeMessage(ex));
            log.error("Task mail delivery failed, planId={}", plan.getId(), ex);
        }
        plan.setUpdatedAt(new Date());
        planMapper.updateById(plan);
    }

    private void completeTask(Long taskId) {
        ConfTaskDO task = taskMapper.selectById(taskId);
        if (task == null || "COMPLETED".equals(task.getTaskStatus())) return;
        Date now = new Date();
        task.setTaskStatus("COMPLETED");
        task.setActualEndTime(now);
        task.setSubmittedAt(now);
        task.setCompletedByName("SYSTEM_MAIL");
        task.setCompletionDesc("邮件经负责人审核后由系统按计划发送");
        task.setUpdateTime(now);
        taskMapper.updateById(task);
    }

    private TaskMailPlanRespDTO toResp(ConfTaskMailPlanDO plan) {
        ConfTaskDO task = taskMapper.selectById(plan.getTaskId());
        List<ContactDTO> recipients = memberService.getParticipantsByRole(plan.getConferenceId(), plan.getTargetRole());
        return TaskMailPlanRespDTO.builder()
                .planId(plan.getId()).conferenceId(plan.getConferenceId()).taskId(plan.getTaskId())
                .taskCode(task == null ? null : task.getTaskCode()).taskName(task == null ? null : task.getTaskName())
                .stageCode(task == null ? null : task.getStageCode())
                .principalRole(task == null ? null : task.getPrincipalRole())
                .principalUserId(task == null ? null : task.getPrincipalUserId())
                .principalName(task == null ? null : task.getPrincipalName())
                .targetRole(plan.getTargetRole()).subject(plan.getSubject())
                .contentBody(plan.getContentBody()).plainBody(toPlainText(plan.getContentBody()))
                .plannedSendTime(plan.getPlannedSendTime()).status(plan.getStatus())
                .recipientCount(recipients == null ? 0 : recipients.size()).recipients(recipients)
                .selectedEmails(parseEmails(plan.getSelectedEmails()))
                .successCount(plan.getSuccessCount()).failCount(plan.getFailCount())
                .generatedAt(plan.getGeneratedAt()).approvedAt(plan.getApprovedAt()).sentAt(plan.getSentAt())
                .errorMessage(plan.getErrorMessage()).build();
    }

    private void requireApprover(Long conferenceId, ConfTaskDO task) {
        String userId = currentUserId();
        if (memberRoleService.isOrganizer(conferenceId, userId)) return;
        try {
            if (task != null && task.getPrincipalUserId() != null && task.getPrincipalUserId().equals(Long.valueOf(userId))) return;
        } catch (NumberFormatException ignored) {
        }
        throw new ClientException("只有任务负责人或会议组织者可以审核邮件");
    }

    private ConfTaskMailPlanDO requirePlan(Long conferenceId, Long planId) {
        ConfTaskMailPlanDO plan = planMapper.selectOne(Wrappers.lambdaQuery(ConfTaskMailPlanDO.class)
                .eq(ConfTaskMailPlanDO::getId, planId).eq(ConfTaskMailPlanDO::getConferenceId, conferenceId).last("limit 1"));
        if (plan == null) throw new ClientException("邮件计划不存在");
        return plan;
    }

    private String resolveTargetRole(String code) {
        if (code == null) return "PROSPECT";
        if (code.contains("REVIEW")) return "REVIEWER";
        if (code.contains("DECISION")) return "AUTHOR";
        if (code.contains("REGISTRATION")) return "ACCEPTED_AUTHOR";
        if (code.contains("ATTENDEE_GUIDE")) return "REGISTERED";
        if (code.contains("THANK_YOU")) return "ATTENDEE";
        if (code.contains("FORMAT_REVISION")) return "AUTHOR";
        return "PROSPECT";
    }

    private String buildSubject(String code, String conference) {
        if (code.contains("REVIEW")) return "【" + conference + "】评审工作通知";
        if (code.contains("DECISION")) return "【" + conference + "】论文评审结果通知";
        if (code.contains("REGISTRATION")) return "【" + conference + "】注册缴费提醒";
        if (code.contains("ATTENDEE_GUIDE")) return "【" + conference + "】参会指南";
        if (code.contains("THANK_YOU")) return "感谢参加 " + conference;
        return "【" + conference + "】征稿与投稿提醒";
    }

    private String buildBody(String code, ConferenceDO conferenceInfo) {
        String conference = conferenceInfo == null ? "本次会议" : conferenceInfo.getTitle();
        String website = conferenceInfo == null || !StringUtils.hasText(conferenceInfo.getWebsiteUrl())
                ? "会议官方网站" : conferenceInfo.getWebsiteUrl();
        String action = code != null && code.contains("REVIEW") ? "请登录会议系统查看评审安排并按时完成评审。"
                : code != null && code.contains("DECISION") ? "论文评审已经完成，请登录会议系统查看结果及后续安排。"
                : code != null && code.contains("REGISTRATION") ? "请在注册截止时间前完成会议注册和缴费。"
                : code != null && code.contains("ATTENDEE_GUIDE") ? "请查收会议日程、签到、交通和会场安排。"
                : code != null && code.contains("THANK_YOU") ? "感谢您参加本次会议，期待再次相聚。"
                : "诚邀您关注会议征稿信息，并在投稿截止时间前提交研究成果。";
        return "您好：\n\n" + action + "\n\n"
                + "会议：" + conference + "\n"
                + "相关信息：" + website + "\n\n"
                + "如有问题，请直接回复本邮件与组委会联系。\n"
                + "此邮件由会议系统生成，经负责人审核后发送。\n\n"
                + conference + " 组委会";
    }

    private String firstFailure(BasicMailSendRespDTO result) {
        return result.getFailedDetails() == null || result.getFailedDetails().isEmpty() ? "发送失败" : result.getFailedDetails().get(0);
    }

    private String safeMessage(Exception ex) {
        String text = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(text)) return "发送失败";
        text = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private String currentUserId() {
        return UserContext.getUserId() == null ? UserContext.getUsername() : UserContext.getUserId();
    }

    private Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    private record MailContent(String subject, String body) {
    }

    /** 将可能含 HTML 标签的正文转换为纯文本，便于审核页查看 */
    private String toPlainText(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        String text = raw;
        if (text.indexOf('<') >= 0) {
            // 块级标签转换行
            text = text.replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
                    .replaceAll("(?i)<li[^>]*>", "\n- ")
                    .replaceAll("(?i)<tr[^>]*>", "\n");
            text = text.replaceAll("<[^>]+>", ""); // 去剩余标签
            text = text.replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
        }
        text = text.replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
        return text;
    }

    private String serializeEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) return null;
        String joined = String.join("\n", emails.stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList());
        return StringUtils.hasText(joined) ? joined : null;
    }

    private List<String> parseEmails(String stored) {
        if (!StringUtils.hasText(stored)) return null;
        List<String> result = java.util.Arrays.stream(stored.split("\\r?\\n"))
                .map(String::trim).filter(StringUtils::hasText).distinct().toList();
        return result.isEmpty() ? null : result;
    }
}
