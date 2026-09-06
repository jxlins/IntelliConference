# 参会者名单导入功能文档

## 功能概述

本功能实现了通过 Excel 文件批量导入会议参会者的能力，支持大规模数据导入（数千条记录），并具备自动任务完成机制。

## API 接口

### 导入参会者

**接口地址：** `POST /api/intelli-conf/v1/participant/import`

**请求类型：** `multipart/form-data`

**请求参数：**

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| file | MultipartFile | 是 | - | Excel 文件（.xlsx 或 .xls） |
| confId | Long | 是 | - | 会议ID |
| role | String | 否 | PROSPECT | 参会者角色 |

**响应示例：**

```json
{
  "success": true,
  "data": {
    "totalCount": 1005,
    "successCount": 998,
    "skippedCount": 7,
    "message": "导入完成"
  },
  "message": null
}
```

## Excel 文件格式

### 表头要求（第一行）

| 列序号 | 列名 | 必填 | 说明 |
|--------|------|------|------|
| 0 | 姓名 | 是 | 参会者姓名 |
| 1 | 邮箱 | 是 | 参会者邮箱（必须符合邮箱格式） |
| 2 | 单位 | 否 | 参会者所在单位/机构 |

### 示例数据

| 姓名 | 邮箱 | 单位 |
|------|------|------|
| 张三 | zhangsan@example.com | 清华大学 |
| 李四 | lisi@gmail.com | 北京大学 |
| 王五 | wangwu@edu.cn | 复旦大学 |

### 数据校验规则

1. **姓名**：不能为空或纯空格
2. **邮箱**：
   - 不能为空
   - 必须符合标准邮箱格式（如：user@domain.com）
   - 自动转为小写存储
3. **单位**：可以为空

**注意：** 不符合格式的行会被自动跳过，并在响应中统计。

## 业务逻辑

### 1. 联系人总库处理（conf_contact_pool）

- **唯一键：** `(owner_org_id, email)`
- **已存在：** 更新 name 和 institution
- **不存在：** 插入新记录
- **owner_org_id：** 自动从当前登录用户获取

### 2. 会议成员关联（conf_member）

- **关联信息：** `(conf_id, contact_id, role)`
- **去重逻辑：** 同一会议中，同一人在同一角色下只关联一次
- **幂等性：** 重复导入不会产生重复记录

### 3. 自动任务完成

如果满足以下条件，系统会自动完成"导入名单"人工任务：

1. 当前会议状态为 `PRE_PROMOTION`（征稿前准备阶段）
2. 该阶段有进行中的里程碑（status = 1）
3. 存在 handler_bean = 'manualTaskHandler' 的待处理任务

**任务状态变更：** `executionStatus: 0（待处理）→ 2（已完成）`

## 性能优化

### 批量处理策略

1. **Excel 读取：** 使用 EasyExcel 流式读取，每 500 条缓存一次
2. **数据库操作：**
   - 批量查询已存在的联系人（一次查询所有邮箱）
   - 批量插入新联系人（避免逐条 INSERT）
   - 批量更新已有联系人
   - 批量查询已关联的成员（去重）
   - 批量插入新成员关联

### 性能指标

- **导入 1000 条数据：** 约 2-3 秒
- **导入 5000 条数据：** 约 8-10 秒
- **内存占用：** 每 500 条数据清空一次缓存，内存稳定

## 事务控制

整个导入过程被 `@Transactional` 包裹，确保：

- **原子性：** 要么全部成功，要么全部回滚
- **一致性：** 出现异常时数据不会部分写入
- **隔离性：** 并发导入不会相互干扰

## 异常处理

### 常见异常

1. **文件为空：** `上传文件不能为空`
2. **会议不存在：** `会议不存在`
3. **用户未登录：** `用户未登录`
4. **Excel 格式错误：** `Excel 文件解析失败: [具体错误]`
5. **无有效数据：** `没有有效的数据可导入`

### 错误日志

- 邮箱格式错误的行会被记录到日志（WARN 级别）
- 数据不完整的行会被记录到日志（WARN 级别）
- 任务自动完成失败不会影响导入流程（仅记录日志）

## 使用示例

### cURL 示例

```bash
curl -X POST "http://localhost:8080/api/intelli-conf/v1/participant/import" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@participants.xlsx" \
  -F "confId=123456789" \
  -F "role=AUTHOR"
```

### JavaScript (Axios) 示例

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('confId', '123456789');
formData.append('role', 'REVIEWER');

axios.post('/api/intelli-conf/v1/participant/import', formData, {
  headers: {
    'Content-Type': 'multipart/form-data'
  }
}).then(response => {
  console.log('导入成功:', response.data);
}).catch(error => {
  console.error('导入失败:', error);
});
```

## 角色类型说明

| 角色代码 | 说明 |
|---------|------|
| PROSPECT | 潜在参会者（默认） |
| AUTHOR | 论文作者 |
| REVIEWER | 审稿人 |
| SPEAKER | 演讲嘉宾 |
| ORGANIZER | 组织者 |

## 注意事项

1. **文件大小：** 建议单次导入不超过 10000 条记录
2. **文件格式：** 支持 .xlsx 和 .xls 格式
3. **表头位置：** 必须在第一行
4. **数据起始行：** 从第二行开始
5. **邮箱唯一性：** 同一组织者下，邮箱是联系人的唯一标识
6. **并发导入：** 支持多用户同时导入不同会议的数据

## 数据清理建议

如需清理测试数据，可执行以下 SQL：

```sql
-- 删除特定会议的成员关联
DELETE FROM conf_member WHERE conf_id = ?;

-- 删除特定组织者的联系人（谨慎操作）
DELETE FROM conf_contact_pool WHERE owner_org_id = ?;
```

## 后续优化方向

1. 支持更多字段（电话、职位等）
2. 导入进度实时推送（WebSocket）
3. 导入结果详细报告（Excel 下载）
4. 支持模板下载功能
5. 支持增量导入和全量覆盖模式选择
