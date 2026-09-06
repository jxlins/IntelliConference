package com.jxl.ai.intelliconf.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteeContext;
import com.jxl.ai.intelliconf.common.biz.portal.PortalCommitteePrincipal;
import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.result.Result;
import com.jxl.ai.intelliconf.common.convention.result.Results;
import com.jxl.ai.intelliconf.dao.entity.ConfLocalDO;
import com.jxl.ai.intelliconf.dao.entity.ConfMilestoneDO;
import com.jxl.ai.intelliconf.dao.entity.ConferenceDO;
import com.jxl.ai.intelliconf.dao.mapper.ConfLocalMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConfMilestoneMapper;
import com.jxl.ai.intelliconf.dao.mapper.ConferenceMapper;
import com.jxl.ai.intelliconf.dto.req.CommitteeMemberInputReqDTO;
import com.jxl.ai.intelliconf.dto.resp.BasicMailSendRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ConferenceRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ContactDTO;
import com.jxl.ai.intelliconf.dto.resp.MilestoneRespDTO;
import com.jxl.ai.intelliconf.dto.resp.PortalCommitteeProfileRespDTO;
import com.jxl.ai.intelliconf.service.BasicMailSendService;
import com.jxl.ai.intelliconf.service.PortalAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外部委员会只读门户接口。
 */
@RestController
@RequiredArgsConstructor
public class PortalController {

    private static final String STATUS_INVITED = "INVITED";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DECLINED = "DECLINED";

    private final ConferenceMapper conferenceMapper;
    private final ConfLocalMapper confLocalMapper;
    private final ConfMilestoneMapper confMilestoneMapper;
        private final PortalAccessService portalAccessService;
        private final BasicMailSendService basicMailSendService;

        @GetMapping(value = "/api/portal/committee/invite", produces = "text/html;charset=UTF-8")
        public String invitePage() {
                PortalCommitteePrincipal principal = requiredPrincipal();
                String name = HtmlUtils.htmlEscape(principal.getName() == null ? principal.getEmail() : principal.getName());
                String role = HtmlUtils.htmlEscape(principal.getRole() == null ? "REVIEWER" : principal.getRole());
                String token = HtmlUtils.htmlEscape(principal.getAccessToken());

            if (STATUS_ACCEPTED.equalsIgnoreCase(principal.getInviteStatus())) {
                return renderDecisionResultPage(true);
            }
            if (STATUS_DECLINED.equalsIgnoreCase(principal.getInviteStatus())) {
                return renderDecisionResultPage(false);
            }

                return """
                                <html><head><meta charset=\"UTF-8\"><title>委员会邀请确认</title></head>
                                <body style=\"font-family:Arial;padding:24px;line-height:1.6\">
                                    <h2>会议委员会邀请确认</h2>
                                    <p>%s，您好。系统邀请您担任 <b>%s</b> 角色，请选择是否接受。</p>
                                    <p>
                                        <a href=\"/api/portal/committee/decision?token=%s&agree=true\" style=\"margin-right:12px\">同意担任</a>
                                        <a href=\"/api/portal/committee/decision?token=%s&agree=false\">暂不担任</a>
                                    </p>
                                </body></html>
                                """.formatted(name, role, token, token);
        }

        @GetMapping(value = "/api/portal/committee/decision", produces = "text/html;charset=UTF-8")
        public String decide(@RequestParam("agree") boolean agree) {
                PortalCommitteePrincipal principal = requiredPrincipal();

            // 已决策状态不再允许回到选择页，也不允许被重复覆盖。
            if (STATUS_ACCEPTED.equalsIgnoreCase(principal.getInviteStatus())) {
                return renderDecisionResultPage(true);
            }
            if (STATUS_DECLINED.equalsIgnoreCase(principal.getInviteStatus())) {
                return renderDecisionResultPage(false);
            }

                portalAccessService.decideByToken(principal.getAccessToken(), agree);

            if (agree && "CHAIR".equalsIgnoreCase(principal.getRole())) {
                sendChairMemberCollectionEmail(principal);
            }

            PortalCommitteePrincipal latest = portalAccessService.validateToken(principal.getAccessToken());
            if (latest != null) {
                if (STATUS_ACCEPTED.equalsIgnoreCase(latest.getInviteStatus())) {
                    return renderDecisionResultPage(true);
                }
                if (STATUS_DECLINED.equalsIgnoreCase(latest.getInviteStatus())) {
                    return renderDecisionResultPage(false);
                }
            }

            // 理论上不会进入这里，兜底按本次选择返回结果页。
            return renderDecisionResultPage(agree);
        }

        @GetMapping(value = "/api/portal/committee/members/form", produces = "text/html;charset=UTF-8")
        public String committeeMembersForm() {
                PortalCommitteePrincipal principal = requiredPrincipal();
                if (!"CHAIR".equalsIgnoreCase(principal.getRole())) {
                        return "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial;padding:24px\"><h3>仅委员会主席可维护名单</h3></body></html>";
                }
                if (!STATUS_ACCEPTED.equalsIgnoreCase(principal.getInviteStatus())) {
                        return "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial;padding:24px\"><h3>请先确认担任委员会主席</h3></body></html>";
                }

                String token = HtmlUtils.htmlEscape(principal.getAccessToken());
                return """
                                <html>
                                <head>
                                    <meta charset=\"UTF-8\">
                                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
                                    <title>录入委员会名单</title>
                                    <style>
                                        :root {
                                            --bg: #f3f7fb;
                                            --card: #ffffff;
                                            --primary: #0f4c81;
                                            --accent: #1f9d8f;
                                            --text: #1f2937;
                                            --muted: #6b7280;
                                            --line: #dbe5ef;
                                            --warn: #f59e0b;
                                        }

                                        * { box-sizing: border-box; }

                                        body {
                                            margin: 0;
                                            background: radial-gradient(circle at 20%% 20%%, #dff3ff 0%%, transparent 45%%),
                                                        radial-gradient(circle at 80%% 10%%, #e2f7f1 0%%, transparent 40%%),
                                                        var(--bg);
                                            color: var(--text);
                                            font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                                            line-height: 1.5;
                                            min-height: 100vh;
                                            padding: 24px;
                                        }

                                        .container {
                                            max-width: 1024px;
                                            margin: 0 auto;
                                        }

                                        .hero {
                                            background: linear-gradient(120deg, #0f4c81, #1766a6);
                                            color: #fff;
                                            border-radius: 18px;
                                            padding: 24px;
                                            box-shadow: 0 14px 30px rgba(15, 76, 129, .25);
                                            margin-bottom: 18px;
                                        }

                                        .hero h2 {
                                            margin: 0 0 8px;
                                            font-size: 24px;
                                            font-weight: 700;
                                        }

                                        .hero p {
                                            margin: 0;
                                            opacity: .92;
                                        }

                                        .card {
                                            background: var(--card);
                                            border: 1px solid var(--line);
                                            border-radius: 16px;
                                            padding: 20px;
                                            box-shadow: 0 8px 20px rgba(16, 24, 40, .06);
                                        }

                                        .block {
                                            margin-bottom: 18px;
                                            border: 1px solid #e8eef6;
                                            border-radius: 12px;
                                            overflow: hidden;
                                        }

                                        .block-header {
                                            display: flex;
                                            justify-content: space-between;
                                            align-items: center;
                                            padding: 12px 14px;
                                            background: #f8fbff;
                                            border-bottom: 1px solid #e8eef6;
                                        }

                                        .block-title {
                                            font-size: 15px;
                                            font-weight: 700;
                                            color: #0f4c81;
                                        }

                                        .btn {
                                            border: 0;
                                            border-radius: 10px;
                                            padding: 8px 12px;
                                            font-size: 13px;
                                            cursor: pointer;
                                            transition: transform .15s ease, opacity .15s ease;
                                        }

                                        .btn:hover { transform: translateY(-1px); }

                                        .btn-add {
                                            background: #e6f7f4;
                                            color: #0c766a;
                                        }

                                        .btn-remove {
                                            background: #fff0f0;
                                            color: #b42318;
                                            padding: 6px 10px;
                                        }

                                        table {
                                            width: 100%%;
                                            border-collapse: collapse;
                                            background: #fff;
                                        }

                                        th, td {
                                            border-bottom: 1px solid #eef2f7;
                                            padding: 10px;
                                            text-align: left;
                                            font-size: 13px;
                                        }

                                        th {
                                            color: #334155;
                                            background: #fbfdff;
                                        }

                                        tr:last-child td { border-bottom: 0; }

                                        input[type=\"email\"],
                                        input[type=\"text\"] {
                                            width: 100%%;
                                            border: 1px solid #d7e1ec;
                                            border-radius: 8px;
                                            padding: 8px 10px;
                                            font-size: 13px;
                                            outline: none;
                                        }

                                        input:focus {
                                            border-color: #60a5fa;
                                            box-shadow: 0 0 0 3px rgba(96, 165, 250, .2);
                                        }

                                        .tips {
                                            margin-top: 8px;
                                            color: var(--muted);
                                            font-size: 12px;
                                        }

                                        .actions {
                                            display: flex;
                                            justify-content: space-between;
                                            align-items: center;
                                            gap: 12px;
                                            margin-top: 16px;
                                            flex-wrap: wrap;
                                        }

                                        .warn {
                                            color: #92400e;
                                            background: #fffbeb;
                                            border: 1px solid #fde68a;
                                            border-radius: 10px;
                                            padding: 9px 12px;
                                            font-size: 12px;
                                        }

                                        .btn-submit {
                                            background: linear-gradient(120deg, var(--primary), #1766a6);
                                            color: #fff;
                                            font-weight: 600;
                                            padding: 10px 18px;
                                        }

                                        @media (max-width: 768px) {
                                            body { padding: 12px; }
                                            .hero { padding: 16px; }
                                            th, td { padding: 8px; }
                                            .block-header { flex-direction: column; align-items: flex-start; gap: 10px; }
                                        }
                                    </style>
                                </head>
                                <body>
                                    <div class=\"container\">
                                        <div class=\"hero\">
                                            <h2>委员会名单维护</h2>
                                            <p>请按角色补充委员会成员。支持动态添加多位成员，提交后将写入会议委员会表。</p>
                                        </div>

                                        <div class=\"card\">
                                            <form id=\"memberForm\" method=\"post\" action=\"/api/portal/committee/members/submit?token=%s\">
                                                <input type=\"hidden\" id=\"payload\" name=\"payload\" />

                                                <div class=\"block\">
                                                    <div class=\"block-header\">
                                                        <span class=\"block-title\">程序委员会（Program Committee）</span>
                                                        <button type=\"button\" class=\"btn btn-add\" onclick=\"addRow('pcTable')\">+ 添加成员</button>
                                                    </div>
                                                    <table id=\"pcTable\">
                                                        <thead>
                                                            <tr>
                                                                <th style=\"width:34%%\">邮箱（必填）</th>
                                                                <th style=\"width:22%%\">姓名</th>
                                                                <th style=\"width:30%%\">单位</th>
                                                                <th style=\"width:14%%\">操作</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td><input type=\"email\" data-field=\"email\" placeholder=\"pc.member@example.com\"></td>
                                                                <td><input type=\"text\" data-field=\"name\" placeholder=\"姓名\"></td>
                                                                <td><input type=\"text\" data-field=\"institution\" placeholder=\"单位\"></td>
                                                                <td><button type=\"button\" class=\"btn btn-remove\" onclick=\"removeRow(this)\">删除</button></td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                                <div class=\"block\">
                                                    <div class=\"block-header\">
                                                        <span class=\"block-title\">会议秘书（Conference Secretary）</span>
                                                        <button type=\"button\" class=\"btn btn-add\" onclick=\"addRow('secTable')\">+ 添加秘书</button>
                                                    </div>
                                                    <table id=\"secTable\">
                                                        <thead>
                                                            <tr>
                                                                <th style=\"width:34%%\">邮箱（必填）</th>
                                                                <th style=\"width:22%%\">姓名</th>
                                                                <th style=\"width:30%%\">单位</th>
                                                                <th style=\"width:14%%\">操作</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            <tr>
                                                                <td><input type=\"email\" data-field=\"email\" placeholder=\"secretary@example.com\"></td>
                                                                <td><input type=\"text\" data-field=\"name\" placeholder=\"姓名\"></td>
                                                                <td><input type=\"text\" data-field=\"institution\" placeholder=\"单位\"></td>
                                                                <td><button type=\"button\" class=\"btn btn-remove\" onclick=\"removeRow(this)\">删除</button></td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>

                                                <div class=\"tips\">提示：主席邮箱无需重复填写，系统会保留主席为 CHAIR，新增成员默认按 REVIEWER 导入。</div>

                                                <div class=\"actions\">
                                                    <div class=\"warn\">请至少填写 1 位成员邮箱后提交。重复邮箱将自动去重并按最后一条信息更新。</div>
                                                    <button type=\"submit\" class=\"btn btn-submit\">提交委员会名单</button>
                                                </div>
                                            </form>
                                        </div>
                                    </div>

                                    <script>
                                        function rowTemplate() {
                                            return `
                                                <tr>
                                                    <td><input type=\"email\" data-field=\"email\" placeholder=\"member@example.com\"></td>
                                                    <td><input type=\"text\" data-field=\"name\" placeholder=\"姓名\"></td>
                                                    <td><input type=\"text\" data-field=\"institution\" placeholder=\"单位\"></td>
                                                    <td><button type=\"button\" class=\"btn btn-remove\" onclick=\"removeRow(this)\">删除</button></td>
                                                </tr>`;
                                        }

                                        function addRow(tableId) {
                                            var tbody = document.querySelector('#' + tableId + ' tbody');
                                            tbody.insertAdjacentHTML('beforeend', rowTemplate());
                                        }

                                        function removeRow(btn) {
                                            var tbody = btn.closest('tbody');
                                            if (tbody.children.length <= 1) {
                                                alert('至少保留一行，若无需该类别成员可留空邮箱。');
                                                return;
                                            }
                                            btn.closest('tr').remove();
                                        }

                                        function collectRows(tableId) {
                                            var rows = document.querySelectorAll('#' + tableId + ' tbody tr');
                                            var lines = [];
                                            rows.forEach(function (row) {
                                                var email = (row.querySelector('[data-field="email"]').value || '').trim();
                                                var name = (row.querySelector('[data-field="name"]').value || '').trim();
                                                var inst = (row.querySelector('[data-field="institution"]').value || '').trim();
                                                if (!email) {
                                                    return;
                                                }
                                                lines.push(email + ',' + name + ',' + inst);
                                            });
                                            return lines;
                                        }

                                        document.getElementById('memberForm').addEventListener('submit', function (e) {
                                            var allLines = [];
                                            allLines = allLines.concat(collectRows('pcTable'));
                                            allLines = allLines.concat(collectRows('secTable'));

                                            if (allLines.length === 0) {
                                                e.preventDefault();
                                                alert('请至少填写一位委员会成员邮箱。');
                                                return;
                                            }

                                            document.getElementById('payload').value = allLines.join('\n');
                                        });
                                    </script>
                                </body>
                                </html>
                                """.formatted(token);
        }

        @PostMapping(value = "/api/portal/committee/members/submit", produces = "text/html;charset=UTF-8")
        public String submitCommitteeMembers(@RequestParam("payload") String payload) {
                PortalCommitteePrincipal principal = requiredPrincipal();
                List<CommitteeMemberInputReqDTO> members = parseMembersPayload(payload);
                int changed = portalAccessService.importMembersByChairToken(principal.getAccessToken(), members);

                return """
                                <html><head><meta charset=\"UTF-8\"></head>
                                <body style=\"font-family:Arial;padding:24px\">
                                    <h3>委员会名单提交成功</h3>
                                    <p>本次新增/更新 %d 条记录。</p>
                                </body></html>
                                """.formatted(changed);
        }

        @PostMapping("/api/portal/committee/members/submit-json")
        public Result<Integer> submitCommitteeMembersJson(@RequestBody List<CommitteeMemberInputReqDTO> members) {
                PortalCommitteePrincipal principal = requiredPrincipal();
                int changed = portalAccessService.importMembersByChairToken(principal.getAccessToken(), members);
                return Results.success(changed);
        }

        private String renderDecisionResultPage(boolean accepted) {
            if (accepted) {
                return "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial;padding:24px\"><h3>已确认担任委员会角色</h3><p>该邀请已生效，后续重复访问此链接将直接显示本确认结果。</p></body></html>";
            }
            return "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial;padding:24px\"><h3>已拒绝本次邀请</h3><p>该邀请已完成处理，后续重复访问此链接将直接显示本结果。若后续希望参与，请联系会议组织者重新发送邀请。</p></body></html>";
        }

    @GetMapping("/api/portal/profile")
    public Result<PortalCommitteeProfileRespDTO> profile() {
        PortalCommitteePrincipal principal = requiredPrincipal();
        return Results.success(PortalCommitteeProfileRespDTO.builder()
                .confId(principal.getConfId())
                .email(principal.getEmail())
                .name(principal.getName())
                .role(principal.getRole())
                .tokenExpireTime(principal.getTokenExpireTime())
                .build());
    }

    @GetMapping("/api/portal/conference")
    public Result<ConferenceRespDTO> conference() {
        Long confId = requiredAcceptedConferenceId();

        ConferenceDO conferenceDO = conferenceMapper.selectById(confId);
        if (conferenceDO == null || Integer.valueOf(1).equals(conferenceDO.getDelFlag())) {
            throw new ClientException("会议不存在或已删除");
        }

        ConferenceRespDTO respDTO = BeanUtil.toBean(conferenceDO, ConferenceRespDTO.class);
        ConfLocalDO confLocalDO = confLocalMapper.selectOne(
                Wrappers.lambdaQuery(ConfLocalDO.class)
                        .eq(ConfLocalDO::getConferId, confId)
                        .eq(ConfLocalDO::getDelFlag, 0)
                        .last("LIMIT 1")
        );
        if (confLocalDO != null) {
            respDTO.setProvince(confLocalDO.getProvince());
            respDTO.setCity(confLocalDO.getCity());
            respDTO.setCountry(confLocalDO.getCountry());
            respDTO.setAddress(confLocalDO.getAddress());
        }

        return Results.success(respDTO);
    }

    @GetMapping("/api/portal/milestones")
    public Result<List<MilestoneRespDTO>> milestones() {
        Long confId = requiredAcceptedConferenceId();

        List<MilestoneRespDTO> result = confMilestoneMapper.selectList(
                        Wrappers.lambdaQuery(ConfMilestoneDO.class)
                                .eq(ConfMilestoneDO::getConfereId, confId)
                                .orderByAsc(ConfMilestoneDO::getStartDate)
                ).stream()
                .map(each -> BeanUtil.toBean(each, MilestoneRespDTO.class))
                .collect(Collectors.toList());

        return Results.success(result);
    }

    private PortalCommitteePrincipal requiredPrincipal() {
        PortalCommitteePrincipal principal = PortalCommitteeContext.get();
        if (principal == null) {
            throw new ClientException("门户令牌无效或已过期");
        }
        return principal;
    }

    private Long requiredConferenceId() {
        Long confId = PortalCommitteeContext.getConferenceId();
        if (confId == null) {
            throw new ClientException("令牌未绑定会议信息");
        }
        return confId;
    }

    private Long requiredAcceptedConferenceId() {
        PortalCommitteePrincipal principal = requiredPrincipal();
        if (!"ACCEPTED".equalsIgnoreCase(principal.getInviteStatus())) {
            throw new ClientException("请先完成委员会邀请确认");
        }
        return requiredConferenceId();
    }

    private void sendChairMemberCollectionEmail(PortalCommitteePrincipal principal) {
        String collectLink = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/portal/committee/members/form")
                .queryParam("token", principal.getAccessToken())
                .toUriString();

        String safeName = StringUtils.hasText(principal.getName()) ? principal.getName().trim() : "老师";
        String subject = "请录入会议委员会名单";
        String body = """
                <p>%s，您好：</p>
                <p>您已确认担任委员会主席，请点击以下链接录入委员会名单（邮箱、姓名、单位）：</p>
                <p><a href=\"%s\" target=\"_blank\">打开名单录入页面</a></p>
                <p>该链接与邀请令牌时效一致。</p>
                """.formatted(safeName, collectLink);

        BasicMailSendRespDTO resp = basicMailSendService.sendBatch(
                principal.getConfId(),
                null,
                subject,
                body,
                List.of(ContactDTO.builder().name(safeName).email(principal.getEmail()).build()),
                false
        );

        if (resp.getSuccessCount() == null || resp.getSuccessCount() < 1) {
            throw new ClientException("主席确认后通知邮件发送失败，请稍后重试");
        }
    }

    private List<CommitteeMemberInputReqDTO> parseMembersPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new ClientException("名单内容不能为空");
        }
        List<CommitteeMemberInputReqDTO> result = new ArrayList<>();
        String[] lines = payload.split("\\r?\\n");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 1 || !StringUtils.hasText(parts[0])) {
                continue;
            }
            CommitteeMemberInputReqDTO dto = new CommitteeMemberInputReqDTO();
            dto.setEmail(parts[0].trim());
            if (parts.length > 1) {
                dto.setName(StringUtils.hasText(parts[1]) ? parts[1].trim() : null);
            }
            if (parts.length > 2) {
                dto.setInstitution(StringUtils.hasText(parts[2]) ? parts[2].trim() : null);
            }
            result.add(dto);
        }
        if (result.isEmpty()) {
            throw new ClientException("未解析到有效的委员会成员邮箱");
        }
        return result;
    }
}
