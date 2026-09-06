package com.jxl.ai.intelliconf.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxl.ai.intelliconf.common.biz.user.UserContext;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.dao.entity.*;
import com.jxl.ai.intelliconf.dao.mapper.*;
import com.jxl.ai.intelliconf.dto.req.ParticipantImportExcelDTO;
import com.jxl.ai.intelliconf.dto.req.ParticipantSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.ParticipantUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantImportRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantPageRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantRespDTO;
import com.jxl.ai.intelliconf.enums.ConferenceRole;
import com.jxl.ai.intelliconf.handler.ParticipantImportListener;
import com.jxl.ai.intelliconf.service.ConferencePermissionService;
import com.jxl.ai.intelliconf.service.MilestoneCompletionChecker;
import com.jxl.ai.intelliconf.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 鍙備細鑰呮湇鍔″疄鐜?
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantServiceImpl implements ParticipantService {

    private final ConfContactPoolMapper contactPoolMapper;
    private final ConfMemberMapper memberMapper;
    private final ConferenceMapper conferenceMapper;
    private final ConfMilestoneMapper milestoneMapper;
    private final SysTaskLogMapper taskLogMapper;
    private final ConfTaskInstanceMapper taskInstanceMapper;
    private final ConfTaskActionLogMapper actionLogMapper;
    private final ConferencePermissionService conferencePermissionService;
    private final MilestoneCompletionChecker milestoneCompletionChecker;

    private Long resolveOwnerOrgId(ConferenceDO conference, Long confId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isNotBlank(userId) && userId.matches("\\d+")) {
            return Long.parseLong(userId);
        }
        String username = UserContext.getUsername();
        if (StrUtil.isNotBlank(username) && username.matches("\\d+")) {
            return Long.parseLong(username);
        }
        String createUser = conference == null ? null : conference.getCreateUser();
        if (StrUtil.isNotBlank(createUser) && createUser.matches("\\d+")) {
            return Long.parseLong(createUser);
        }
        // contact_pool.owner_org_id is only used as a contact scope here. In this
        // project user ids can be usernames, so fall back to the conference scope.
        return confId == null ? 0L : confId;
    }

    private String normalizeEmail(String email) {
        return StrUtil.isBlank(email) ? email : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParticipantImportRespDTO importParticipants(MultipartFile file, Long confId) {
        // 1. 鍙傛暟鏍￠獙
        if (file == null || file.isEmpty()) {
            throw new ClientException("涓婁紶鏂囦欢涓嶈兘涓虹┖");
        }
        if (confId == null) {
            throw new ClientException("浼氳ID涓嶈兘涓虹┖");
        }

        conferencePermissionService.requireAtLeastRole(confId, ConferenceRole.OPERATOR);

        // 2. 鏍￠獙浼氳鏄惁瀛樺湪
        ConferenceDO conference = conferenceMapper.selectById(confId);
        if (conference == null) {
            throw new ClientException("会议不存在");
        }

        // 3. 鑾峰彇褰撳墠鐢ㄦ埛鐨勭粍缁嘔D
        Long ownerOrgId = resolveOwnerOrgId(conference, confId);
        String userIdStr = String.valueOf(ownerOrgId);
        if (StrUtil.isBlank(userIdStr)) {
            throw new ClientException("用户未登录");
        }
        ownerOrgId = Long.parseLong(userIdStr);

        // 4. 浣跨敤 EasyExcel 瑙ｆ瀽鏂囦欢
        ParticipantImportListener listener = new ParticipantImportListener();
        try {
            EasyExcel.read(file.getInputStream(), ParticipantImportExcelDTO.class, listener)
                    .sheet()
                    .headRowNumber(0)
                    .doRead();
        } catch (IOException e) {
            log.error("瑙ｆ瀽 Excel 鏂囦欢澶辫触", e);
            throw new ClientException("Excel 鏂囦欢瑙ｆ瀽澶辫触: " + e.getMessage());
        }

        List<ParticipantImportExcelDTO> validDataList = listener.getAllDataList();
        int skippedCount = listener.getSkippedCount();

        if (validDataList.isEmpty()) {
            return ParticipantImportRespDTO.builder()
                    .totalCount(skippedCount)
                    .successCount(0)
                    .skippedCount(skippedCount)
                    .message("娌℃湁鏈夋晥鐨勬暟鎹彲瀵煎叆")
                    .build();
        }

        // 5. 鎵归噺澶勭悊 contact_pool锛堝幓閲?+ upsert锛?
        Map<String, Long> emailToContactIdMap = batchUpsertContacts(ownerOrgId, validDataList);

        // 6. 鎵归噺鎻掑叆 conf_member锛堝幓閲嶏級
        int insertedMemberCount = batchInsertMembers(confId, "PROSPECT", emailToContactIdMap);

        // 7. 濡傛灉褰撳墠闃舵鏄?PRE_PROMOTION锛岃嚜鍔ㄥ畬鎴?瀵煎叆鍚嶅崟"浠诲姟
        tryCompleteImportTask(confId);
        tryCompleteNewImportTask(confId);

        // 8. 杩斿洖瀵煎叆缁撴灉
        return ParticipantImportRespDTO.builder()
                .totalCount(validDataList.size() + skippedCount)
                .successCount(insertedMemberCount)
                .skippedCount(skippedCount + (validDataList.size() - insertedMemberCount))
                .message("瀵煎叆瀹屾垚")
                .build();
    }

    /**
     * 鎵归噺 upsert 鑱旂郴浜哄埌 contact_pool
     * 杩斿洖 email -> contactId 鐨勬槧灏?
     */
    private Map<String, Long> batchUpsertContacts(Long ownerOrgId, List<ParticipantImportExcelDTO> dataList) {
        Map<String, Long> emailToContactIdMap = new HashMap<>();
        dataList.forEach(item -> item.setEmail(normalizeEmail(item.getEmail())));

        // 鎻愬彇鎵€鏈夐偖绠?
        Set<String> emailSet = dataList.stream()
                .map(ParticipantImportExcelDTO::getEmail)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (emailSet.isEmpty()) {
            return emailToContactIdMap;
        }

        // 鎵归噺鏌ヨ宸插瓨鍦ㄧ殑鑱旂郴浜?
        List<ConfContactPoolDO> existingContacts = contactPoolMapper.selectList(
                Wrappers.lambdaQuery(ConfContactPoolDO.class)
                        .eq(ConfContactPoolDO::getOwnerOrgId, ownerOrgId)
                        .in(ConfContactPoolDO::getEmail, emailSet)
        );

        Map<String, ConfContactPoolDO> existingEmailMap = existingContacts.stream()
                .collect(Collectors.toMap(ConfContactPoolDO::getEmail, c -> c));

        // Deduplicate emails within this batch to avoid unique constraint conflicts.
        Map<String, ConfContactPoolDO> pendingInsertMap = new LinkedHashMap<>();
        Map<String, ConfContactPoolDO> pendingUpdateMap = new LinkedHashMap<>();

        for (ParticipantImportExcelDTO data : dataList) {
            String email = data.getEmail();
            if (StrUtil.isBlank(email)) {
                continue;
            }

            ConfContactPoolDO existing = existingEmailMap.get(email);

            if (existing != null) {
                // 宸插瓨鍦細鏇存柊 name 鍜?institution
                existing.setName(data.getName());
                existing.setInstitution(data.getInstitution());
                pendingUpdateMap.put(email, existing);
                emailToContactIdMap.put(email, existing.getId());
            } else {
                ConfContactPoolDO pending = pendingInsertMap.get(email);
                if (pending != null) {
                    pending.setName(data.getName());
                    pending.setInstitution(data.getInstitution());
                    continue;
                }
                // 涓嶅瓨鍦細鍑嗗鎻掑叆
                ConfContactPoolDO newContact = ConfContactPoolDO.builder()
                        .ownerOrgId(ownerOrgId)
                        .name(data.getName())
                        .email(email)
                        .institution(data.getInstitution())
                        .build();
                pendingInsertMap.put(email, newContact);
            }
        }

        List<ConfContactPoolDO> toInsert = new ArrayList<>(pendingInsertMap.values());
        List<ConfContactPoolDO> toUpdate = new ArrayList<>(pendingUpdateMap.values());

        // 鎵归噺鎻掑叆鏂拌仈绯讳汉
        if (!toInsert.isEmpty()) {
            // MyBatis-Plus 鐨?saveBatch 浼氳嚜鍔ㄥ～鍏呬富閿?
            toInsert.forEach(contactPoolMapper::insert);
            toInsert.forEach(c -> emailToContactIdMap.put(c.getEmail(), c.getId()));
            log.info("Batch inserted contacts: {}", toInsert.size());
        }

        // 鎵归噺鏇存柊宸叉湁鑱旂郴浜?
        if (!toUpdate.isEmpty()) {
            toUpdate.forEach(c -> contactPoolMapper.updateById(c));
            log.info("鎵归噺鏇存柊 {} 鏉″凡鏈夎仈绯讳汉", toUpdate.size());
        }

        return emailToContactIdMap;
    }

    /**
     * 鎵归噺鎻掑叆浼氳鎴愬憳鍏宠仈锛堥槻姝㈤噸澶嶏級
     */
    private int batchInsertMembers(Long confId, String role, Map<String, Long> emailToContactIdMap) {
        Set<Long> contactIds = new HashSet<>(emailToContactIdMap.values());
        if (contactIds.isEmpty()) {
            return 0;
        }

        // 鏌ヨ宸茬粡鍏宠仈鐨?contact_id
        List<ConfMemberDO> existingMembers = memberMapper.selectList(
                Wrappers.lambdaQuery(ConfMemberDO.class)
                        .eq(ConfMemberDO::getConfId, confId)
                        .eq(ConfMemberDO::getRole, role)
                        .in(ConfMemberDO::getContactId, contactIds)
        );

        Set<Long> existingContactIds = existingMembers.stream()
                .map(ConfMemberDO::getContactId)
                .collect(Collectors.toSet());

        // 杩囨护鍑洪渶瑕佹彃鍏ョ殑 contact_id
        List<ConfMemberDO> toInsert = contactIds.stream()
                .filter(contactId -> !existingContactIds.contains(contactId))
                .map(contactId -> ConfMemberDO.builder()
                        .confId(confId)
                        .contactId(contactId)
                        .role(role)
                        .build())
                .collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            toInsert.forEach(memberMapper::insert);
            log.info("Batch inserted conference members: {}", toInsert.size());
            return toInsert.size();
        }

        return 0;
    }

    /**
     * 灏濊瘯鑷姩瀹屾垚"瀵煎叆鍚嶅崟"浠诲姟
     * 濡傛灉褰撳墠浼氳澶勪簬 PRE_PROMOTION 闃舵锛屼笖瀛樺湪瀵瑰簲鐨勪汉宸ヤ换鍔★紝鍒欒嚜鍔ㄥ畬鎴?
     */
    private void tryCompleteImportTask(Long confId) {
        try {
            log.info("寮€濮嬪皾璇曡嚜鍔ㄥ畬鎴愬鍏ュ悕鍗曚换鍔? confId={}", confId);
            
            // 鏌ヨ浼氳褰撳墠鐘舵€?
            ConferenceDO conference = conferenceMapper.selectById(confId);
            if (conference == null) {
                log.warn("浼氳涓嶅瓨鍦? confId={}", confId);
                return;
            }
            
            log.info("浼氳褰撳墠鐘舵€? confId={}, currentState={}", confId, conference.getCurrentState());
            
            if (!"INITIATION".equals(conference.getCurrentState())) {
                log.info("浼氳鐘舵€佷笉鏄?INITIATION锛岃烦杩囪嚜鍔ㄥ畬鎴? confId={}, currentState={}", 
                        confId, conference.getCurrentState());
                return;
            }

            // 鏌ヨ INITIATION 闃舵鐨勮繘琛屼腑閲岀▼纰?
            ConfMilestoneDO milestone = milestoneMapper.selectOne(
                    Wrappers.lambdaQuery(ConfMilestoneDO.class)
                            .eq(ConfMilestoneDO::getConfereId, confId)
                            .eq(ConfMilestoneDO::getNodeCode, "INITIATION")
                            .eq(ConfMilestoneDO::getStatus, 1) // 杩涜涓?
                            .last("LIMIT 1")
            );

            if (milestone == null) {
                log.warn("鏈壘鍒拌繘琛屼腑鐨?INITIATION 閲岀▼纰? confId={}", confId);
                return;
            }
            
            log.info("鎵惧埌杩涜涓殑閲岀▼纰? confId={}, milestoneId={}, nodeCode={}, status={}", 
                    confId, milestone.getId(), milestone.getNodeCode(), milestone.getStatus());

            // 鏌ヨ"瀵煎叆鍚嶅崟"浜哄伐浠诲姟锛坔andler_bean = 'manualTaskHandler'锛屾鍦ㄦ墽琛屼腑锛?
            LambdaQueryWrapper<SysTaskLogDO> taskQuery = Wrappers.lambdaQuery(SysTaskLogDO.class)
                    .eq(SysTaskLogDO::getConfereId, confId)
                    .eq(SysTaskLogDO::getMilestoneId, milestone.getId())
                    .eq(SysTaskLogDO::getHandlerBean, "manualTaskHandler")
                    .eq(SysTaskLogDO::getExecutionStatus, 1); // 鎵ц涓?

            List<SysTaskLogDO> taskLogs = taskLogMapper.selectList(taskQuery);
            
            log.info("鎵惧埌鐨勪汉宸ヤ换鍔℃暟閲? confId={}, milestoneId={}, taskCount={}", 
                    confId, milestone.getId(), taskLogs.size());

            // 鏌ユ壘浠诲姟鍚嶇О鍖呭惈"瀵煎叆"鎴?鍚嶅崟"鐨勪换鍔★紝鐩存帴鏇存柊涓哄凡瀹屾垚
            int completedCount = 0;
            for (SysTaskLogDO taskLog : taskLogs) {
                log.info("鍑嗗瀹屾垚浠诲姟: confId={}, taskLogId={}, handlerBean={}, status={}", 
                        confId, taskLog.getId(), taskLog.getHandlerBean(), taskLog.getExecutionStatus());
                
                taskLog.setExecutionStatus(2); // 2 = 宸插畬鎴?
                taskLog.setEndTime(new Date());
                taskLogMapper.updateById(taskLog);
                completedCount++;
                
                log.info("鑷姩瀹屾垚瀵煎叆鍚嶅崟浠诲姟: confId={}, taskLogId={}, milestoneId={}", 
                        confId, taskLog.getId(), milestone.getId());
            }
            
            if (completedCount > 0) {
                // 鍏抽敭锛氳皟鐢?MilestoneCompletionChecker 妫€鏌ラ噷绋嬬鏄惁瀹屾垚
                log.info("璋冪敤閲岀▼纰戝畬鎴愭鏌? confId={}, milestoneId={}, completedTaskCount={}", 
                        confId, milestone.getId(), completedCount);
                milestoneCompletionChecker.checkAndComplete(milestone.getId());
            } else {
                log.info("娌℃湁鎵惧埌闇€瑕佽嚜鍔ㄥ畬鎴愮殑浠诲姟: confId={}, milestoneId={}", confId, milestone.getId());
            }

        } catch (Exception e) {
            // 浠诲姟鑷姩瀹屾垚澶辫触涓嶅奖鍝嶅鍏ユ祦绋?
            log.error("鑷姩瀹屾垚瀵煎叆鍚嶅崟浠诲姟澶辫触: confId={}", confId, e);
        }
    }

    private void tryCompleteNewImportTask(Long confId) {
        try {
            List<ConfTaskInstanceDO> tasks = taskInstanceMapper.selectList(
                    Wrappers.lambdaQuery(ConfTaskInstanceDO.class)
                            .eq(ConfTaskInstanceDO::getConferenceId, confId)
                            .in(ConfTaskInstanceDO::getStatus, List.of("PENDING", "PROCESSING"))
            );
            Date now = new Date();
            String operator = StrUtil.blankToDefault(
                    StrUtil.blankToDefault(UserContext.getUsername(), UserContext.getUserId()),
                    "SYSTEM");
            int completed = 0;
            for (ConfTaskInstanceDO task : tasks) {
                if (!isPotentialAuthorImportTask(task)) {
                    continue;
                }
                String before = task.getStatus();
                task.setStatus("COMPLETED");
                task.setCurrentHandlerId(operator);
                task.setCurrentHandlerName(operator);
                task.setUpdatedAt(now);
                taskInstanceMapper.updateById(task);
                actionLogMapper.insert(ConfTaskActionLogDO.builder()
                        .taskInstanceId(task.getId())
                        .conferenceId(confId)
                        .operatorId(operator)
                        .operatorName(operator)
                        .operatorRoleCode("ORGANIZER")
                        .actionType("COMPLETE")
                        .comment("涓婁紶娼滃湪浣滆€呭悕鍗曞悗鑷姩瀹屾垚")
                        .beforeStatus(before)
                        .afterStatus("COMPLETED")
                        .createdAt(now)
                        .build());
                completed++;
            }
            if (completed > 0) {
                log.info("Completed potential author import task instances, confId={}, completed={}", confId, completed);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-complete potential author import tasks, confId={}", confId, e);
        }
    }

    private boolean isPotentialAuthorImportTask(ConfTaskInstanceDO task) {
        String text = String.join(" ",
                StrUtil.blankToDefault(task.getTaskCode(), ""),
                StrUtil.blankToDefault(task.getTaskName(), ""),
                StrUtil.blankToDefault(task.getParams(), "")).toLowerCase(Locale.ROOT);
        return text.contains("potential_author")
                || text.contains("potential author")
                || text.contains("prospect")
                || text.contains("participant")
                || text.contains("潜在作者")
                || text.contains("作者名单");
    }

    @Override
    public ParticipantPageRespDTO getParticipantList(Long confId, String keyword, Long current, Long size) {
        // 鍙傛暟鏍￠獙
        if (confId == null) {
            throw new ClientException("浼氳ID涓嶈兘涓虹┖");
        }

        conferencePermissionService.requireAtLeastRole(confId, ConferenceRole.VIEWER);

        // 榛樿鍒嗛〉鍙傛暟
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 10L;
        }
        if (size > 100) {
            size = 100L; // 闄愬埗鏈€澶ф瘡椤?00鏉?
        }

        // 鏋勫缓鏌ヨ鏉′欢
        LambdaQueryWrapper<ConfMemberDO> memberQuery = Wrappers.lambdaQuery(ConfMemberDO.class)
                .eq(ConfMemberDO::getConfId, confId)
                .orderByDesc(ConfMemberDO::getCreateTime);

        // 鍒嗛〉鏌ヨ浼氳鎴愬憳
        Page<ConfMemberDO> memberPage = new Page<>(current, size);
        memberMapper.selectPage(memberPage, memberQuery);

        List<ConfMemberDO> members = memberPage.getRecords();

        if (members.isEmpty()) {
            return ParticipantPageRespDTO.builder()
                    .records(new ArrayList<>())
                    .current(current)
                    .size(size)
                    .total(0L)
                    .pages(0L)
                    .build();
        }

        // 鎻愬彇鎵€鏈?contactId
        Set<Long> contactIds = members.stream()
                .map(ConfMemberDO::getContactId)
                .collect(Collectors.toSet());

        // 鎵归噺鏌ヨ鑱旂郴浜轰俊鎭?
        LambdaQueryWrapper<ConfContactPoolDO> contactQuery = Wrappers.lambdaQuery(ConfContactPoolDO.class)
                .in(ConfContactPoolDO::getId, contactIds);

        // 濡傛灉鏈夋悳绱㈠叧閿瘝锛屾坊鍔犳悳绱㈡潯浠?
        if (StrUtil.isNotBlank(keyword)) {
            contactQuery.and(wrapper -> wrapper
                    .like(ConfContactPoolDO::getName, keyword)
                    .or()
                    .like(ConfContactPoolDO::getEmail, keyword)
                    .or()
                    .like(ConfContactPoolDO::getInstitution, keyword)
            );
        }

        List<ConfContactPoolDO> contacts = contactPoolMapper.selectList(contactQuery);

        // 鏋勫缓 contactId -> contact 鐨勬槧灏?
        Map<Long, ConfContactPoolDO> contactMap = contacts.stream()
                .collect(Collectors.toMap(ConfContactPoolDO::getId, c -> c));

        // 缁勮鍝嶅簲鏁版嵁锛堣繃婊ゆ帀涓嶇鍚堟悳绱㈡潯浠剁殑璁板綍锛?
        List<ParticipantRespDTO> participantList = members.stream()
                .filter(member -> contactMap.containsKey(member.getContactId()))
                .map(member -> {
                    ConfContactPoolDO contact = contactMap.get(member.getContactId());
                    return ParticipantRespDTO.builder()
                            .id(member.getId())
                            .contactId(member.getContactId())
                            .name(contact.getName())
                            .email(contact.getEmail())
                            .institution(contact.getInstitution())
                            .role(member.getRole())
                            .createTime(member.getCreateTime())
                            .build();
                })
                .collect(Collectors.toList());

        // 濡傛灉鏈夊叧閿瘝鎼滅储锛岄渶瑕侀噸鏂拌绠楁€绘暟
        Long totalCount = memberPage.getTotal();
        if (StrUtil.isNotBlank(keyword)) {
            // 杩欓噷绠€鍖栧鐞嗭紝瀹為檯鎬绘暟鍙兘涓嶅噯纭?
            // 鏇翠弗璋ㄧ殑鍋氭硶鏄娇鐢?JOIN 鏌ヨ
            totalCount = (long) participantList.size();
        }

        return ParticipantPageRespDTO.builder()
                .records(participantList)
                .current(current)
                .size(size)
                .total(totalCount)
                .pages((totalCount + size - 1) / size)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addParticipant(ParticipantSaveReqDTO reqDTO) {
        // 鍙傛暟鏍￠獙
        if (reqDTO == null) {
            throw new ClientException("鍙傛暟涓嶈兘涓虹┖");
        }
        if (reqDTO.getConfId() == null) {
            throw new ClientException("浼氳ID涓嶈兘涓虹┖");
        }

        conferencePermissionService.requireAtLeastRole(reqDTO.getConfId(), ConferenceRole.OPERATOR);
        if (StrUtil.isBlank(reqDTO.getName())) {
            throw new ClientException("濮撳悕涓嶈兘涓虹┖");
        }
        if (StrUtil.isBlank(reqDTO.getEmail())) {
            throw new ClientException("閭涓嶈兘涓虹┖");
        }

        // 鏍￠獙浼氳鏄惁瀛樺湪
        ConferenceDO conference = conferenceMapper.selectById(reqDTO.getConfId());
        if (conference == null) {
            throw new ClientException("会议不存在");
        }

        // 鑾峰彇褰撳墠鐢ㄦ埛鐨勭粍缁嘔D
        Long ownerOrgId = resolveOwnerOrgId(conference, reqDTO.getConfId());
        String userIdStr = String.valueOf(ownerOrgId);
        if (StrUtil.isBlank(userIdStr)) {
            throw new ClientException("用户未登录");
        }
        ownerOrgId = Long.parseLong(userIdStr);

        // 鏌ヨ鑱旂郴浜烘槸鍚﹀凡瀛樺湪锛堟牴鎹偖绠憋級
        ConfContactPoolDO existingContact = contactPoolMapper.selectOne(
                Wrappers.lambdaQuery(ConfContactPoolDO.class)
                        .eq(ConfContactPoolDO::getOwnerOrgId, ownerOrgId)
                        .eq(ConfContactPoolDO::getEmail, reqDTO.getEmail())
                        .last("LIMIT 1")
        );

        Long contactId;

        if (existingContact != null) {
            // 鑱旂郴浜哄凡瀛樺湪锛屾洿鏂颁俊鎭?
            existingContact.setName(reqDTO.getName());
            existingContact.setInstitution(reqDTO.getInstitution());
            contactPoolMapper.updateById(existingContact);
            contactId = existingContact.getId();
            log.info("鏇存柊宸叉湁鑱旂郴浜? contactId={}, email={}", contactId, reqDTO.getEmail());
        } else {
            // 鑱旂郴浜轰笉瀛樺湪锛屾柊寤?
            ConfContactPoolDO newContact = ConfContactPoolDO.builder()
                    .ownerOrgId(ownerOrgId)
                    .name(reqDTO.getName())
                    .email(reqDTO.getEmail())
                    .institution(reqDTO.getInstitution())
                    .build();
            contactPoolMapper.insert(newContact);
            contactId = newContact.getId();
            log.info("鍒涘缓鏂拌仈绯讳汉: contactId={}, email={}", contactId, reqDTO.getEmail());
        }

        // 妫€鏌ユ槸鍚﹀凡鍦ㄤ細璁腑
        Long existingMemberCount = memberMapper.selectCount(
                Wrappers.lambdaQuery(ConfMemberDO.class)
                        .eq(ConfMemberDO::getConfId, reqDTO.getConfId())
                        .eq(ConfMemberDO::getContactId, contactId)
        );

        if (existingMemberCount > 0) {
            throw new ClientException("该邮箱已在当前会议中");
        }

        // 娣诲姞浼氳鎴愬憳鍏宠仈
        String role = StrUtil.isNotBlank(reqDTO.getRole()) ? reqDTO.getRole() : "PROSPECT";
        ConfMemberDO member = ConfMemberDO.builder()
                .confId(reqDTO.getConfId())
                .contactId(contactId)
                .role(role)
                .build();
        memberMapper.insert(member);

        log.info("娣诲姞鍙備細鑰呮垚鍔? confId={}, contactId={}, role={}", 
                reqDTO.getConfId(), contactId, role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateParticipant(ParticipantUpdateReqDTO reqDTO) {
        // 鍙傛暟鏍￠獙
        if (reqDTO == null || reqDTO.getMemberId() == null) {
            throw new ClientException("鍙傛暟涓嶈兘涓虹┖");
        }

        // 鏌ヨ浼氳鎴愬憳鏄惁瀛樺湪
        ConfMemberDO member = memberMapper.selectById(reqDTO.getMemberId());
        if (member == null) {
            throw new ClientException("鍙備細鑰呬笉瀛樺湪");
        }

        conferencePermissionService.requireAtLeastRole(member.getConfId(), ConferenceRole.OPERATOR);

        // 鏌ヨ鑱旂郴浜轰俊鎭?
        ConfContactPoolDO contact = contactPoolMapper.selectById(member.getContactId());
        if (contact == null) {
            throw new ClientException("鑱旂郴浜轰俊鎭笉瀛樺湪");
        }

        // 鏇存柊鑱旂郴浜轰俊鎭紙鍙洿鏂伴潪绌哄瓧娈碉級
        boolean contactUpdated = false;
        if (StrUtil.isNotBlank(reqDTO.getName())) {
            contact.setName(reqDTO.getName());
            contactUpdated = true;
        }
        if (StrUtil.isNotBlank(reqDTO.getEmail())) {
            // 妫€鏌ユ柊閭鏄惁宸茶鍏朵粬鑱旂郴浜轰娇鐢?
            ConfContactPoolDO existingContact = contactPoolMapper.selectOne(
                    Wrappers.lambdaQuery(ConfContactPoolDO.class)
                            .eq(ConfContactPoolDO::getOwnerOrgId, contact.getOwnerOrgId())
                            .eq(ConfContactPoolDO::getEmail, reqDTO.getEmail())
                            .ne(ConfContactPoolDO::getId, contact.getId())
                            .last("LIMIT 1")
            );
            if (existingContact != null) {
                throw new ClientException("璇ラ偖绠卞凡琚叾浠栬仈绯讳汉浣跨敤");
            }
            contact.setEmail(reqDTO.getEmail());
            contactUpdated = true;
        }
        if (StrUtil.isNotBlank(reqDTO.getInstitution())) {
            contact.setInstitution(reqDTO.getInstitution());
            contactUpdated = true;
        }

        if (contactUpdated) {
            contactPoolMapper.updateById(contact);
            log.info("鏇存柊鑱旂郴浜轰俊鎭? contactId={}", contact.getId());
        }

        // 鏇存柊浼氳鎴愬憳瑙掕壊
        if (StrUtil.isNotBlank(reqDTO.getRole())) {
            member.setRole(reqDTO.getRole());
            memberMapper.updateById(member);
            log.info("鏇存柊浼氳鎴愬憳瑙掕壊: memberId={}, newRole={}", member.getId(), reqDTO.getRole());
        }

        log.info("鏇存柊鍙備細鑰呮垚鍔? memberId={}, contactId={}", reqDTO.getMemberId(), member.getContactId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeParticipant(Long memberId) {
        // 鍙傛暟鏍￠獙
        if (memberId == null) {
            throw new ClientException("鍙備細鑰匢D涓嶈兘涓虹┖");
        }

        // 鏌ヨ璁板綍鏄惁瀛樺湪
        ConfMemberDO member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new ClientException("鍙備細鑰呬笉瀛樺湪");
        }

        conferencePermissionService.requireAtLeastRole(member.getConfId(), ConferenceRole.OPERATOR);

        // 鍒犻櫎浼氳鎴愬憳鍏宠仈
        int deleted = memberMapper.deleteById(memberId);
        if (deleted > 0) {
            log.info("鍒犻櫎鍙備細鑰呮垚鍔? memberId={}, confId={}, contactId={}", 
                    memberId, member.getConfId(), member.getContactId());
        } else {
            throw new ClientException("删除参会者失败");
        }
    }
}
