# Potential Author Discovery Tool

本文档说明“潜在投稿者自动发现 / 公开邮箱采集”工具的整体流程、代码位置、接口、数据库表和合规边界。

## 1. 工具目标

工具名称：

```text
PotentialAuthorDiscoveryTool
```

核心目标：

- 根据会议主题关键词发现近几年相关论文。
- 从论文元数据中解析作者、机构、论文标题、年份、DOI、开放获取页面和 PDF 链接。
- 在合规边界内访问公开页面和 PDF，提取公开学术联系邮箱。
- 对候选作者进行主题相关性、邮箱可信度和综合得分计算。
- 只保存能绑定到可信邮箱的潜在投稿者。
- 自动发现结果默认进入待审核状态，不自动发送邮件。
- 审核通过后，将候选人加入联系人池和会议成员表。

## 2. 代码位置

后端核心代码集中在：

```text
intelliconf/src/main/java/com/jxl/ai/intelliconf/author_discovery
```

主要子目录：

```text
config/       # 配置项
controller/   # REST API
dto/          # 请求、响应和内部传输对象
entity/       # 数据库实体
enums/        # 状态枚举
repository/   # Mapper 和仓储封装
service/      # 主流程、OpenAlex、Crossref、邮箱提取、评分
tool/         # 对外工具入口和任务处理器
util/         # robots.txt、身份匹配、文本工具
```

前端入口在：

```text
IntelliConfFront/src/views/ConferenceUserManagementView.vue
```

数据库 SQL 在：

```text
intelliconf/src/main/resources/database/conf_author_discovery.sql
```

相关联系人/成员表 SQL：

```text
intelliconf/src/main/resources/database/conf_contact_pool.sql
intelliconf/src/main/resources/database/conf_member.sql
```

## 3. 整体流程

### 3.1 创建发现任务

```tex
创建发现任务
→ 读取会议主题关键词
→ 调用 OpenAlex 检索相关论文
→ 解析论文作者和机构
→ 合并成候选作者
→ 抓取论文网页和 PDF 中的公开邮箱
→ 做作者身份匹配
→ 计算主题相关度、邮箱可信度、综合分
→ 写入潜在投稿者候选库
→ 管理员审核通过或驳回
```

前端在会议用户管理页面点击“发现潜在投稿者”，填写主题关键词、年份范围、最大候选人数等参数。

后端接口：

```text
POST /api/conferences/{conferenceId}/author-discovery/jobs
```

后端会创建一条任务记录：

```text
conf_author_discovery_job
```

任务初始状态为：

```text
PENDING
```

### 3.2 执行发现任务

调试版当前是同步执行：

```text
POST /api/author-discovery/jobs/{jobId}/run
```

代码中已经保留 TODO，后续可切换为异步任务执行。

执行时主流程在：

```text
AuthorDiscoveryService.runJob
```

流程：

1. 将任务状态改为 `RUNNING`。
2. 读取任务中的 topic keywords、年份范围、最大候选人数。
3. 调用 `OpenAlexClient` 查询相关论文。
4. 可选调用 `CrossrefClient` 补充 DOI 元数据。
5. 通过 `AuthorCandidateBuilder` 将论文展开成作者候选。
6. 通过 `PublicEmailExtractor` 从公开页面和 PDF 中提取邮箱证据。
7. 通过 `AuthorIdentityMatcher` 判断邮箱是否能绑定到作者。
8. 通过 `CandidateScoringService` 计算主题相关性、邮箱可信度、综合得分。
9. 只保存有可信邮箱的候选作者。
10. 更新任务统计，状态改为 `COMPLETED`。

如果 OpenAlex 完全不可用，任务会变为：

```text
FAILED
```

单篇论文、单个 URL、Crossref 或邮箱提取失败不会中断整个任务。

## 4. 邮箱采集逻辑

邮箱提取类：

```text
PublicEmailExtractor
```

只访问 OpenAlex 或 Crossref 元数据中已经提供的链接：

- `landing_page_url`
- `best_oa_location.landing_page_url`
- `best_oa_location.pdf_url`
- 页面中明确指向 PDF 的链接

不会访问：

- 搜索引擎结果页
- 登录页
- 验证码页
- 需要付费或绕过权限的页面
- 反爬受限页面

访问前会使用：

```text
RobotsTxtChecker
```

检查目标站点 `robots.txt` 是否允许访问。

HTML 页面处理：

1. 下载公开 HTML。
2. 移除 `script`、`style`、`noscript`。
3. 提取正文文本。
4. 提取标准邮箱。
5. 解析 `mailto:` 链接。
6. 识别常见邮箱混淆写法，例如 `name [at] example [dot] edu`。
7. 保存邮箱附近文本作为证据。

PDF 处理：

1. 只处理公开可访问 PDF。
2. 使用 PDFBox 解析文本。
3. 只读取前 1 到 2 页，避免过度下载和处理。
4. 提取邮箱和附近证据文本。

邮箱过滤：

- 过滤 `example.com`、`test.com`、`invalid.com`、`localhost`。
- 过滤或降低通用邮箱可信度，例如 `noreply`、`support`、`info`、`admin`、`editorial`、`contact`。

## 5. 作者身份匹配

身份匹配类：

```text
AuthorIdentityMatcher
```

邮箱不会因为“页面中出现了邮箱”就直接绑定到作者。系统会综合判断：

- 页面文本中是否出现作者全名。
- 页面文本中是否出现作者名的反转形式。
- 邮箱 local-part 是否包含作者姓氏或名字。
- 页面文本中是否出现作者机构。
- 页面文本中是否出现代表论文标题核心词。
- OpenAlex 是否标记作者为通信作者。
- 邮箱附近文本是否出现 correspondence、email 等提示。

身份匹配分数达到阈值，且不是通用邮箱，才会写入候选作者主邮箱字段。

如果发现邮箱但身份依据不足，该邮箱不会作为候选人的可联系邮箱。

## 6. 评分规则

评分类：

```text
CandidateScoringService
```

### 6.1 主题相关性

字段：

```text
topic_similarity
```

取值范围：

```text
0.0000 - 1.0000
```

计算依据：

- 论文标题是否命中会议主题关键词。
- OpenAlex topics / keywords 是否命中会议主题关键词。
- 作者多篇论文命中时分数提升。

### 6.2 邮箱可信度

字段：

```text
email_confidence
```

典型规则：

```text
PDF 页面 + 通信作者证据          0.95
PDF 页面 + 作者姓名匹配          0.85
mailto 链接 + 身份匹配           0.88
开放获取页面 + 作者/机构匹配      0.82
普通落地页 + 机构匹配            0.65
只有邮箱但身份依据不足           0.30
```

当前系统只保存能绑定到可信邮箱的候选作者。

### 6.3 综合得分

字段：

```text
overall_score
```

公式：

```text
overall_score =
  topic_similarity * 0.55
  + email_confidence * 0.30
  + recent_activity * 0.10
  + citation_or_publication_score * 0.05
```

## 7. 数据库表

### 7.1 conf_author_discovery_job

发现任务表。

关键字段：

```text
id
conference_id
topic_keywords
year_from
year_to
max_authors
status
total_papers
total_candidates
high_confidence_emails
error_message
created_by
created_at
started_at
finished_at
```

状态：

```text
PENDING
RUNNING
COMPLETED
FAILED
```

### 7.2 conf_potential_author

潜在投稿者候选表。

关键字段：

```text
id
conference_id
discovery_job_id
author_name
normalized_name
email
organization
country_region
research_keywords
representative_papers
source_platform
source_url
topic_similarity
email_confidence
overall_score
review_status
contact_status
created_at
updated_at
```

审核状态：

```text
PENDING
APPROVED
REJECTED
```

联系状态：

```text
NOT_CONTACTED
OPT_OUT
BLOCKED
```

当前规则：没有可信邮箱的作者不会写入此表。

### 7.3 conf_author_email_source

邮箱证据表。

每个邮箱证据必须记录来源和证据文本。

关键字段：

```text
id
potential_author_id
email
source_type
source_url
evidence_text
confidence
collected_at
```

来源类型：

```text
PAPER_PDF
OPEN_ACCESS_PAGE
PUBLISHER_PAGE
AUTHOR_PAGE
HTML_BODY
MAILTO
```

### 7.4 conf_email_suppression

屏蔽邮箱表。

用途：

- 退订
- 投诉
- 退信
- 手动屏蔽

关键字段：

```text
id
conference_id
email
reason
created_at
```

原因：

```text
OPT_OUT
BOUNCED
COMPLAINT
MANUAL_BLOCK
```

保存候选作者前会检查该表。如果邮箱被屏蔽，不会写入可联系邮箱字段。

### 7.5 conf_contact_pool

联系人池。

审核通过潜在投稿者时，如果邮箱不存在，会新增联系人。

关键字段：

```text
id
owner_org_id
name
email
institution
create_time
```

### 7.6 conf_member

会议成员表。

审核通过潜在投稿者时，会将联系人加入当前会议成员表。

关键字段：

```text
id
conf_id
contact_id
role
create_time
```

潜在投稿者审核通过后的默认角色：

```text
PROSPECT
```

## 8. 接口说明

### 8.1 创建任务

```text
POST /api/conferences/{conferenceId}/author-discovery/jobs
```

请求示例：

```json
{
  "topicKeywords": [
    "Artificial Intelligence in Education",
    "Learning Analytics",
    "Educational Data Mining"
  ],
  "yearFrom": 2021,
  "yearTo": 2026,
  "maxAuthors": 200,
  "enableCrossref": true,
  "enableEmailExtraction": true
}
```

响应示例：

```json
{
  "jobId": 1,
  "status": "PENDING"
}
```

### 8.2 执行任务

```text
POST /api/author-discovery/jobs/{jobId}/run
```

响应示例：

```json
{
  "jobId": 1,
  "status": "COMPLETED",
  "totalCandidates": 80,
  "highConfidenceEmails": 35
}
```

### 8.3 查询任务

```text
GET /api/author-discovery/jobs/{jobId}
```

### 8.4 查询候选作者

```text
GET /api/conferences/{conferenceId}/potential-authors
```

支持参数：

```text
reviewStatus
contactStatus
minScore
sourcePlatform
hasEmail
```

### 8.5 审核候选作者

```text
POST /api/potential-authors/{authorId}/approve
POST /api/potential-authors/{authorId}/reject
```

审核通过后：

1. 更新 `conf_potential_author.review_status = APPROVED`。
2. 将作者写入或更新 `conf_contact_pool`。
3. 将联系人加入 `conf_member`。
4. 不会自动发送邮件。

## 9. 配置项

配置文件：

```text
intelliconf/src/main/resources/application.yml
```

配置：

```yaml
author-discovery:
  openalex-base-url: https://api.openalex.org
  crossref-base-url: https://api.crossref.org
  user-agent: ConferenceAuthorDiscoveryBot
  contact-email:
  request-timeout-seconds: 60
  max-page-bytes: 5242880
  max-html-bytes: 5242880
  max-pdf-bytes: 15728640
  max-discovered-pdf-per-page: 3
  respect-robots: true
  robots-failure-policy: SKIP
  request-interval-ms: 200
  max-retry: 2
  max-papers-factor: 5
  max-total-papers: 500
  pdf-parse-max-pages: 2
```

## 10. 合规边界

工具明确遵守以下限制：

- 只处理公开可访问页面。
- 抓取前检查 robots.txt。
- 不访问搜索引擎结果页。
- 不绕过登录、验证码、付费墙或反爬限制。
- 不自动发送邮件。
- 发现结果默认 `PENDING`，必须人工审核。
- 邮箱证据必须保存来源 URL、来源类型、证据文本和可信度。
- 被 `conf_email_suppression` 屏蔽的邮箱不得进入可联系字段。

## 11. 调试建议

最小调试流程：

1. 执行数据库 SQL：

```text
conf_author_discovery.sql
conf_contact_pool.sql
conf_member.sql
```

2. 启动后端：

```bash
cd intelliconf
mvn spring-boot:run
```

3. 启动前端：

```bash
cd IntelliConfFront
npm install
npm run dev
```

4. 进入会议用户管理页面，点击“发现潜在投稿者”。

5. 填写主题关键词并执行任务。

6. 查看候选作者列表，确认邮箱、机构、综合得分和邮箱可信度。

7. 点击“通过”或“拒绝”完成审核。

## 12. 常见问题

### 为什么候选作者数量比论文作者少？

当前规则只保存可以绑定到可信邮箱的作者。没有可信邮箱、邮箱为通用邮箱、身份匹配不足或邮箱被 suppression 表屏蔽的作者不会进入候选库。

### 为什么有些论文页面没有提取到邮箱？

可能原因：

- 页面被 robots.txt 禁止访问。
- 页面需要登录、验证码或付费。
- 页面没有公开邮箱。
- 邮箱是图片或复杂脚本渲染。
- 邮箱存在但无法和具体作者建立足够强的身份匹配。

### 审核通过后会自动发邮件吗？

不会。审核通过只会加入联系人池和会议成员表。邮件发送模块必须另行读取已审核通过且不在 suppression 表中的联系人。
