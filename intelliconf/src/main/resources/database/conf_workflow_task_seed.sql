-- IntelliConf 标准会议流程任务模板。
-- 使用阶段内相对日期，会议关键日期调整后可重新生成计划而无需修改模板。

UPDATE conf_task_def
SET default_role = 'SECRETARY', default_assignee_role = 'SECRETARY'
WHERE default_role = 'SECRETARIAT' OR default_assignee_role = 'SECRETARIAT';

UPDATE conf_task
SET principal_role = 'SECRETARY'
WHERE principal_role = 'SECRETARIAT';

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time, created_at, updated_at)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW(), NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'DEFINE_REVIEW_POLICY' task_code, '确认评审规则与评分标准' task_name, '确认评审轮次、评分维度、利益冲突和录用规则' task_desc, 'RULE_CONFIG' task_type, 'PROGRAM_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 2 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'RECRUIT_REVIEWERS', '补充并确认审稿人库', '根据投稿主题和工作量补充审稿专家', 'MEMBER_MANAGEMENT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 5, 1, 0
    UNION ALL SELECT 'CHECK_CONFLICTS_OF_INTEREST', '检查利益冲突', '检查作者、机构与审稿人之间的利益冲突', 'SYSTEM_CHECK', 'PROGRAM_CHAIR', 'SYSTEM_CHECK', 30, 'STAGE_START_TIME', 1, 4, 1, 0
    UNION ALL SELECT 'ASSIGN_REVIEWERS', '分派论文评审任务', '按研究方向、负载和利益冲突为论文分派审稿人', 'ASSIGNMENT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 2, 7, 1, 1
    UNION ALL SELECT 'SEND_REVIEW_INVITATIONS', '发送审稿邀请', '向已分派的审稿人发送邀请和评审说明', 'EMAIL', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 3, 8, 1, 0
    UNION ALL SELECT 'MONITOR_REVIEW_PROGRESS', '跟踪评审进度', '持续检查评审完成率、拒审和超时情况', 'DATA_MONITOR', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 7, 28, 0, 0
    UNION ALL SELECT 'SEND_REVIEW_REMINDERS', '发送评审提醒', '对临近截止且未完成的审稿人发送提醒', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 70, 'STAGE_END_TIME', -14, -7, 0, 0
    UNION ALL SELECT 'MANAGE_REBUTTAL', '组织作者答辩与复议', '开放作者回复并汇总争议论文', 'COMMUNICATION', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', -14, -7, 0, 0
    UNION ALL SELECT 'HOLD_DECISION_MEETING', '召开录用决策会议', '汇总评审意见并形成录用建议', 'DECISION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', -7, -3, 1, 1
    UNION ALL SELECT 'RECORD_ACCEPTANCE_DECISIONS', '确认并录入录用结果', '完成录用、拒稿及候补结果录入', 'SYSTEM_OPERATION', 'PROGRAM_CHAIR', 'SYSTEM_CHECK', 100, 'STAGE_END_TIME', -5, -2, 1, 1
    UNION ALL SELECT 'PREPARE_DECISION_EMAILS', '生成录用结果邮件', '按论文结果生成作者通知邮件草稿', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 110, 'STAGE_END_TIME', -4, -1, 0, 1
    UNION ALL SELECT 'SEND_DECISION_NOTICES', '发送录用结果通知', '审核后向所有投稿作者发送评审结果', 'EMAIL', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 120, 'STAGE_END_TIME', 0, 0, 1, 1
    UNION ALL SELECT 'GENERATE_REVIEW_REPORT', '生成评审阶段报告', '统计投稿、评审、录用率和异常处理情况', 'REPORT', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 130, 'STAGE_END_TIME', 0, 2, 0, 0
) v ON s.stage_code = 'REVIEW'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW(), updated_at=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time, created_at, updated_at)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW(), NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'OPEN_REGISTRATION' task_code, '开放作者注册通道' task_name, '配置注册类别、费用、支付和发票规则' task_desc, 'SYSTEM_CONFIG' task_type, 'REGISTRATION_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 2 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'PUBLISH_REGISTRATION_GUIDE', '发布注册与缴费指南', '在会议网站发布注册流程、费用和退款说明', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 3, 1, 1
    UNION ALL SELECT 'COLLECT_CAMERA_READY_PAPERS', '收集论文终稿', '跟踪作者终稿提交并处理缺失材料', 'DATA_MONITOR', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 1, 15, 1, 0
    UNION ALL SELECT 'VERIFY_CAMERA_READY_FORMAT', '检查终稿格式', '检查终稿页数、模板、作者和元数据', 'MANUAL_REVIEW', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 5, 18, 1, 0
    UNION ALL SELECT 'COLLECT_COPYRIGHT_FORMS', '收集出版授权文件', '跟踪版权协议或出版授权文件', 'FILE_UPLOAD', 'PUBLICATION_CHAIR', 'FILE_UPLOAD', 50, 'STAGE_START_TIME', 5, 20, 1, 0
    UNION ALL SELECT 'TRACK_AUTHOR_REGISTRATION', '跟踪作者注册状态', '核对每篇录用论文至少一位作者完成注册', 'DATA_MONITOR', 'REGISTRATION_CHAIR', 'SYSTEM_CHECK', 60, 'STAGE_START_TIME', 3, 25, 1, 0
    UNION ALL SELECT 'VERIFY_REGISTRATION_PAYMENTS', '核对注册缴费与发票', '核对支付结果并处理异常订单和发票需求', 'FINANCE', 'FINANCE_OFFICER', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 3, 25, 0, 0
    UNION ALL SELECT 'SEND_REGISTRATION_REMINDERS', '发送注册缴费提醒', '向尚未完成注册的录用作者发送提醒', 'EMAIL', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', -14, -3, 0, 0
    UNION ALL SELECT 'CLOSE_EARLY_BIRD_REGISTRATION', '关闭早鸟注册', '切换注册价格并保存早鸟阶段统计', 'SYSTEM_OPERATION', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', 0, 0, 0, 0
    UNION ALL SELECT 'CONFIRM_PRESENTER_LIST', '确认论文报告人名单', '确认报告人、报告方式和缺席安排', 'DATA_REVIEW', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 100, 'STAGE_END_TIME', -5, 0, 1, 1
    UNION ALL SELECT 'GENERATE_REGISTRATION_REPORT', '生成注册阶段报告', '统计注册、缴费、国家地区和人员类型', 'REPORT', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 110, 'STAGE_END_TIME', 0, 3, 0, 0
) v ON s.stage_code = 'REGISTRATION'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW(), updated_at=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time, created_at, updated_at)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW(), NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'DESIGN_CONFERENCE_PROGRAM' task_code, '设计会议议程框架' task_name, '确定分会场、主题论坛和时间分配' task_desc, 'PROGRAM' task_type, 'PROGRAM_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 14 end_offset_days, 1 is_core, 1 need_review
    UNION ALL SELECT 'CONFIRM_KEYNOTE_SPEAKERS', '确认主旨报告嘉宾', '完成嘉宾邀请、报告题目和行程确认', 'COMMUNICATION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 20, 1, 1
    UNION ALL SELECT 'COLLECT_PRESENTER_MATERIALS', '收集报告人与演讲材料', '收集简介、照片、演讲题目和授权材料', 'FILE_UPLOAD', 'PROGRAM_CHAIR', 'FILE_UPLOAD', 30, 'STAGE_START_TIME', 7, 30, 0, 0
    UNION ALL SELECT 'SCHEDULE_TECHNICAL_SESSIONS', '编排技术分会场', '按照主题和报告形式完成论文排期', 'PROGRAM', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 7, 30, 1, 1
    UNION ALL SELECT 'CONFIRM_VENUE_AND_ROOMS', '确认场地与分会场', '锁定会场、容量、动线和设备配置', 'LOGISTICS', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_END_TIME', -45, -30, 1, 1
    UNION ALL SELECT 'ARRANGE_CATERING_AND_HOTELS', '安排餐饮与住宿', '确认茶歇、餐饮、推荐酒店和交通信息', 'LOGISTICS', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 60, 'STAGE_END_TIME', -30, -7, 0, 0
    UNION ALL SELECT 'PROCESS_VISA_INVITATION_LETTERS', '处理签证邀请函', '审核注册信息并出具参会邀请文件', 'DOCUMENT', 'SECRETARY', 'MANUAL_CONFIRM', 70, 'STAGE_END_TIME', -45, -14, 0, 0
    UNION ALL SELECT 'PREPARE_CONFERENCE_MATERIALS', '准备会议物料', '制作胸卡、指引、签到资料和会场物料', 'LOGISTICS', 'LOCAL_CHAIR', 'FILE_UPLOAD', 80, 'STAGE_END_TIME', -21, -3, 1, 0
    UNION ALL SELECT 'PUBLISH_FINAL_PROGRAM', '发布最终版会议日程', '审核后在网站发布最终议程和报告安排', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_END_TIME', -14, -7, 1, 1
    UNION ALL SELECT 'SEND_ATTENDEE_GUIDE', '发送参会指南', '向已注册人员发送交通、签到和日程说明', 'EMAIL', 'SECRETARY', 'MANUAL_CONFIRM', 100, 'STAGE_END_TIME', -7, -3, 0, 1
    UNION ALL SELECT 'CONDUCT_SYSTEM_REHEARSAL', '完成会议系统与设备彩排', '验证直播、投影、音频、网络和签到系统', 'SYSTEM_CHECK', 'LOCAL_CHAIR', 'SYSTEM_CHECK', 110, 'STAGE_END_TIME', -5, -2, 1, 0
    UNION ALL SELECT 'FINAL_READINESS_CHECK', '执行会前最终检查', '按清单确认人员、场地、议程、物料和应急预案', 'SYSTEM_CHECK', 'GENERAL_CHAIR', 'SYSTEM_CHECK', 120, 'STAGE_END_TIME', -2, -1, 1, 1
) v ON s.stage_code = 'CONFERENCE_PREPARATION'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW(), updated_at=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time, created_at, updated_at)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW(), NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'OPEN_ONSITE_REGISTRATION' task_code, '开放现场签到' task_name, '启用签到台并处理现场注册问题' task_desc, 'ONSITE' task_type, 'REGISTRATION_CHAIR' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 0 end_offset_days, 1 is_core, 0 need_review
    UNION ALL SELECT 'RUN_OPENING_CEREMONY', '执行开幕式流程', '按议程完成致辞、介绍和开幕环节', 'ONSITE', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 0, 1, 0
    UNION ALL SELECT 'OPERATE_TECHNICAL_SESSIONS', '运行技术分会场', '协调主持人、报告人、计时和问答', 'ONSITE', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 0, 3, 1, 0
    UNION ALL SELECT 'SUPPORT_SPEAKERS', '支持嘉宾与报告人', '处理演讲材料、设备和临时变更', 'ONSITE', 'PROGRAM_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'OPERATE_VENUE_SERVICES', '运行会场保障', '保障场地、餐饮、交通、网络和应急支持', 'ONSITE', 'LOCAL_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 0, 3, 1, 0
    UNION ALL SELECT 'HANDLE_ATTENDEE_SUPPORT', '处理参会者服务请求', '集中响应签到、日程、发票和会场咨询', 'COMMUNICATION', 'SECRETARY', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'TRACK_DAILY_ATTENDANCE', '统计每日签到与参会情况', '形成每日签到、会场容量和缺席记录', 'DATA_MONITOR', 'REGISTRATION_CHAIR', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 0, 3, 0, 0
    UNION ALL SELECT 'RUN_CLOSING_CEREMONY', '执行闭幕式与奖项发布', '完成总结、奖项发布和下一届会议预告', 'ONSITE', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 80, 'STAGE_END_TIME', 0, 0, 1, 0
) v ON s.stage_code = 'CONFERENCE_DAYS'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW(), updated_at=NOW();

INSERT INTO conf_task_def
    (stage_def_id, task_code, task_name, task_desc, task_type, default_role, completion_type,
     sort_order, offset_base, start_offset_days, end_offset_days, is_core, need_review, status,
     create_time, update_time, created_at, updated_at)
SELECT s.id, v.task_code, v.task_name, v.task_desc, v.task_type, v.default_role, v.completion_type,
       v.sort_order, v.offset_base, v.start_offset_days, v.end_offset_days, v.is_core, v.need_review, 1,
       NOW(), NOW(), NOW(), NOW()
FROM conf_stage_def s
JOIN (
    SELECT 'SEND_THANK_YOU_EMAILS' task_code, '发送会后感谢邮件' task_name, '向参会者、嘉宾、委员和志愿者发送感谢邮件' task_desc, 'EMAIL' task_type, 'SECRETARY' default_role, 'MANUAL_CONFIRM' completion_type, 10 sort_order, 'STAGE_START_TIME' offset_base, 0 start_offset_days, 3 end_offset_days, 0 is_core, 1 need_review
    UNION ALL SELECT 'COLLECT_PARTICIPANT_FEEDBACK', '收集参会反馈', '发送问卷并汇总议程、会务和场地反馈', 'SURVEY', 'SECRETARY', 'MANUAL_CONFIRM', 20, 'STAGE_START_TIME', 0, 14, 0, 0
    UNION ALL SELECT 'ISSUE_CERTIFICATES', '发放参会与报告证书', '核对签到和报告记录后生成电子证书', 'DOCUMENT', 'SECRETARY', 'MANUAL_CONFIRM', 30, 'STAGE_START_TIME', 2, 14, 0, 0
    UNION ALL SELECT 'PUBLISH_CONFERENCE_RECAP', '发布会议新闻与回顾', '发布会议照片、数据和成果摘要', 'WEBSITE', 'PUBLICITY_CHAIR', 'MANUAL_CONFIRM', 40, 'STAGE_START_TIME', 3, 10, 0, 1
    UNION ALL SELECT 'PUBLISH_PROCEEDINGS', '完成论文集出版与发布', '提交最终出版材料并核对检索信息', 'PUBLICATION', 'PUBLICATION_CHAIR', 'MANUAL_CONFIRM', 50, 'STAGE_START_TIME', 1, 30, 1, 1
    UNION ALL SELECT 'SETTLE_CONFERENCE_FINANCES', '完成会议财务结算', '核对收入、支出、报销、税务和供应商付款', 'FINANCE', 'FINANCE_OFFICER', 'MANUAL_CONFIRM', 60, 'STAGE_START_TIME', 1, 21, 1, 1
    UNION ALL SELECT 'FOLLOW_UP_SPONSORS', '完成赞助商回访', '交付赞助权益报告并完成合作回访', 'COMMUNICATION', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 70, 'STAGE_START_TIME', 7, 30, 0, 0
    UNION ALL SELECT 'ARCHIVE_CONFERENCE_MATERIALS', '归档会议资料与数据', '归档论文、评审、注册、财务、邮件和现场资料', 'ARCHIVE', 'SECRETARY', 'FILE_UPLOAD', 80, 'STAGE_START_TIME', 7, 30, 1, 0
    UNION ALL SELECT 'PREPARE_FINAL_CONFERENCE_REPORT', '编制会议总结报告', '汇总学术质量、参会数据、财务与改进建议', 'REPORT', 'GENERAL_CHAIR', 'MANUAL_CONFIRM', 90, 'STAGE_START_TIME', 14, 45, 1, 1
    UNION ALL SELECT 'CLOSE_CONFERENCE_PROJECT', '完成会议项目关闭', '确认所有核心事务完成并将会议归档', 'SYSTEM_CHECK', 'GENERAL_CHAIR', 'SYSTEM_CHECK', 100, 'STAGE_END_TIME', -5, 0, 1, 1
) v ON s.stage_code = 'POST_CONFERENCE'
ON DUPLICATE KEY UPDATE
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    default_role=VALUES(default_role), completion_type=VALUES(completion_type), sort_order=VALUES(sort_order),
    offset_base=VALUES(offset_base), start_offset_days=VALUES(start_offset_days), end_offset_days=VALUES(end_offset_days),
    is_core=VALUES(is_core), need_review=VALUES(need_review), status=1, update_time=NOW(), updated_at=NOW();

-- 所有模板就绪后再次执行幂等补齐，覆盖后续五个阶段。
INSERT INTO conf_task
    (conference_id, stage_id, task_def_id, stage_code, task_code, task_name, task_desc, task_type,
     principal_role, planned_start_time, planned_end_time, actual_start_time, task_status, priority,
     risk_level, is_core, completion_type, need_review, sort_order, create_time, update_time)
SELECT q.conference_id, q.stage_id, q.task_def_id, q.stage_code, q.task_code, q.task_name, q.task_desc, q.task_type,
       q.principal_role, q.planned_start_time, q.planned_end_time,
       CASE WHEN q.planned_start_time <= NOW() THEN NOW() ELSE NULL END,
       CASE WHEN q.planned_start_time <= NOW() THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,
       'MEDIUM', CASE WHEN q.planned_end_time < NOW() THEN 'OVERDUE' ELSE 'NORMAL' END,
       q.is_core, q.completion_type, q.need_review, q.sort_order, NOW(), NOW()
FROM (
    SELECT c.id conference_id, si.id stage_id, d.id task_def_id, sd.stage_code, d.task_code,
           d.task_name, d.task_desc, d.task_type,
           COALESCE(NULLIF(d.default_role, ''), d.default_assignee_role, 'GENERAL_CHAIR') principal_role,
           CASE WHEN d.start_offset_days IS NULL THEN si.planned_start_time ELSE DATE_ADD(
               COALESCE(CASE d.offset_base
                   WHEN 'CONFERENCE_CREATE_TIME' THEN c.create_time
                   WHEN 'PAPER_SUBMISSION_DEADLINE' THEN c.paper_submission_deadline
                   WHEN 'NOTIFICATION_OF_ACCEPTANCE' THEN c.notification_of_acceptance
                   WHEN 'CAMERA_READY_SUBMISSION' THEN c.camera_ready_submission
                   WHEN 'EARLY_BIRD_REGISTRATION' THEN c.early_bird_registration
                   WHEN 'CONFERENCE_START_DATE' THEN c.conference_start_date
                   WHEN 'CONFERENCE_END_DATE' THEN c.conference_end_date
                   WHEN 'STAGE_END_TIME' THEN si.planned_end_time
                   ELSE si.planned_start_time END, si.planned_start_time),
               INTERVAL d.start_offset_days DAY) END planned_start_time,
           CASE WHEN d.end_offset_days IS NULL THEN si.planned_end_time ELSE DATE_ADD(
               COALESCE(CASE d.offset_base
                   WHEN 'CONFERENCE_CREATE_TIME' THEN c.create_time
                   WHEN 'PAPER_SUBMISSION_DEADLINE' THEN c.paper_submission_deadline
                   WHEN 'NOTIFICATION_OF_ACCEPTANCE' THEN c.notification_of_acceptance
                   WHEN 'CAMERA_READY_SUBMISSION' THEN c.camera_ready_submission
                   WHEN 'EARLY_BIRD_REGISTRATION' THEN c.early_bird_registration
                   WHEN 'CONFERENCE_START_DATE' THEN c.conference_start_date
                   WHEN 'CONFERENCE_END_DATE' THEN c.conference_end_date
                   WHEN 'STAGE_START_TIME' THEN si.planned_start_time
                   ELSE si.planned_end_time END, si.planned_end_time),
               INTERVAL d.end_offset_days DAY) END planned_end_time,
           COALESCE(d.is_core, 0) is_core,
           COALESCE(NULLIF(d.completion_type, ''), 'MANUAL_CONFIRM') completion_type,
           COALESCE(d.need_review, 0) need_review, COALESCE(d.sort_order, 0) sort_order
    FROM conferences c
    JOIN conf_stage si ON si.conference_id = c.id
    JOIN conf_stage_def sd ON sd.id = si.stage_def_id AND sd.status = 1
    JOIN conf_task_def d ON d.stage_def_id = sd.id AND d.status = 1
    WHERE c.del_flag = 0 AND c.setup_status = 'TASK_GENERATED'
) q
ON DUPLICATE KEY UPDATE
    stage_id=VALUES(stage_id), task_def_id=VALUES(task_def_id), stage_code=VALUES(stage_code),
    task_name=VALUES(task_name), task_desc=VALUES(task_desc), task_type=VALUES(task_type),
    principal_role=VALUES(principal_role), planned_start_time=VALUES(planned_start_time),
    planned_end_time=VALUES(planned_end_time), is_core=VALUES(is_core),
    completion_type=VALUES(completion_type), need_review=VALUES(need_review),
    sort_order=VALUES(sort_order), update_time=NOW();
