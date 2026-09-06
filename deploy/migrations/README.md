# IEIR2026 数据库合并

## 文件与边界

- `20260907_merge_ieir2026.sql`：非破坏式、事务化、可重复执行的合并脚本。
- `20260907_verify_ieir2026.sql`：数量、孤儿引用、`admin`、`dn`、SMTP 隔离验收。
- 源文件 `conference.sql` SHA-256：
  `765C89F1A56BF5983382B2BF34CD2E57C286461F573D02E8CC3BC16D3F68865C`。

脚本只读取隔离源库 `legacy_source`，写入客户端当前选中的数据库。原 dump 含 `DROP TABLE IF EXISTS`，只允许恢复到 `legacy_source`，不得直接输入目标库。

明确排除：

- `conference_mail_account` 及其中的 `password_cipher`；
- `mail_send_task` 待发送队列；
- `conf_task_mail_plan` 旧计划；
- 与 IEIR2026 无关的旧会议任务实例和日志。

历史 `mail_send_log` 只作为不可执行的审计记录合并。

## 当前线上形态

- MariaDB：宿主机 `mariadb.service`，本地 Unix socket 可由系统 root 管理；
- 后端：`intelliconf-backend.service`；
- Redis、网关：`intelliconf-redis`、`intelliconf-gateway` 容器；
- 正式库：`intelliconf`。

不要套用旧的 `docker exec intelliconf-db` 命令。

## 1. 完整备份

```bash
set -euo pipefail
ts="$(date +%Y%m%d-%H%M%S)"
backup_dir="/opt/intelliconf/backups/$ts"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

mariadb-dump --single-transaction --routines --triggers --events \
  --hex-blob --default-character-set=utf8mb4 intelliconf \
  > "$backup_dir/intelliconf-full.sql"
mariadb -N -B intelliconf \
  -e "SELECT * FROM sys_user WHERE username='admin'; \
      SELECT * FROM conferences WHERE short_name='dn';" \
  > "$backup_dir/preserved-admin-dn.tsv"
gzip -9 -c "$backup_dir/intelliconf-full.sql" \
  > "$backup_dir/intelliconf-full.sql.gz"
(
  cd "$backup_dir"
  sha256sum intelliconf-full.sql intelliconf-full.sql.gz \
    preserved-admin-dn.tsv > SHA256SUMS
  gzip -t intelliconf-full.sql.gz
  sha256sum -c SHA256SUMS
)
```

## 2. 恢复线上备份克隆与隔离源库

```bash
mariadb -e "DROP DATABASE IF EXISTS intelliconf_clone_20260907;
CREATE DATABASE intelliconf_clone_20260907
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP DATABASE IF EXISTS legacy_source;
CREATE DATABASE legacy_source
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mariadb intelliconf_clone_20260907 \
  < "$backup_dir/intelliconf-full.sql"
mariadb legacy_source < conference.sql
```

## 3. 克隆库演练与幂等验收

先保存克隆库中的 `admin`、`dn` 全行快照。随后连续执行两次合并，并在每次后执行验收：

```bash
mariadb -N -B intelliconf_clone_20260907 \
  -e "SELECT * FROM sys_user WHERE username='admin';
      SELECT * FROM conferences WHERE short_name='dn';" \
  > /tmp/preserved-before.tsv

mariadb intelliconf_clone_20260907 < 20260907_merge_ieir2026.sql
mariadb intelliconf_clone_20260907 < 20260907_verify_ieir2026.sql
mariadb intelliconf_clone_20260907 < 20260907_merge_ieir2026.sql
mariadb intelliconf_clone_20260907 < 20260907_verify_ieir2026.sql

mariadb -N -B intelliconf_clone_20260907 \
  -e "SELECT * FROM sys_user WHERE username='admin';
      SELECT * FROM conferences WHERE short_name='dn';" \
  > /tmp/preserved-after.tsv
cmp /tmp/preserved-before.tsv /tmp/preserved-after.tsv
```

验收条件：

1. `admin_count=1`、`dn_count=1`，且两次全行快照逐字节一致；
2. 每个数量行 `actual=expected`，旧模板映射数为 82；
3. 所有 `orphan_count=0`；
4. `ieir_smtp_accounts=0`、`ieir_send_queue=0`；
5. 第二次合并前后所有目标表行数一致；
6. IEIR2026 的 `create_user` 保持旧账号用户名 `xiaohong`，兼容当前前端会议列表筛选。

## 4. 正式合并与验收

```bash
mariadb intelliconf < 20260907_merge_ieir2026.sql
mariadb intelliconf < 20260907_verify_ieir2026.sql
```

再次导出 `admin`、`dn` 并与备份快照比较；再导出一份 post-merge 全库备份。应用服务无需重启。

完成后删除含旧 SMTP 密文的隔离源库和源 dump，保留 SHA-256、迁移脚本、验收输出与备份：

```bash
mariadb -e "DROP DATABASE IF EXISTS legacy_source;
DROP DATABASE IF EXISTS intelliconf_clone_20260907;"
rm -f conference.sql
```

## 5. 回滚

如正式验收失败，先阻止业务继续写库，再从预迁移完整备份恢复到新库验证：

```bash
systemctl stop intelliconf-backend
mariadb -e "DROP DATABASE IF EXISTS intelliconf_restore;
CREATE DATABASE intelliconf_restore
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mariadb intelliconf_restore < "$backup_dir/intelliconf-full.sql"
```

在 `intelliconf_restore` 通过完整验收后，维护窗口内做受控库切换；不得把未经验证的空库直接覆盖正式库。恢复完成后再启动 `intelliconf-backend.service`。

## 2026-09-07 已执行结果

- 预迁移备份：`/opt/intelliconf/backups/20260907-005835/`；
- 线上备份恢复克隆后，最终脚本连续执行两次，数量不变；
- `admin` 与 `dn` 全行快照一致；
- IEIR2026：1 个会议、7 个阶段、82 个任务、452 个成员、89 个潜在作者、5 个发现任务、467 条关联邮箱来源、13 条任务日志、1 个附件、3 条邀请、7 条历史邮件日志；
- 82 条旧任务模板全部映射，其中向当前 54 条模板集合补入 28 条；
- SMTP 账号 0、发送队列 0、全部孤儿检查 0；
- 正式库已完成合并，MariaDB、后端、Redis、网关均保持运行，HTTP 18080 返回 200；
- `legacy_source`、演练克隆库及服务器上的源 dump 已清理。
