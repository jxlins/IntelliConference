# IntelliConference database backup

The production service stores business data in the `intelliconf` MariaDB
database. The timer creates a transaction-consistent compressed logical dump
once per day and keeps 30 days of verified backups.

## Production paths

- Script: `/opt/intelliconf/shared/bin/intelliconf-db-backup.sh`
- Unit: `/etc/systemd/system/intelliconf-db-backup.service`
- Timer: `/etc/systemd/system/intelliconf-db-backup.timer`
- Backups: `/opt/intelliconf/backups/database/YYYYMMDD/`
- Latest backup directory: `/opt/intelliconf/backups/database/latest`

Each dump has a sibling `.sha256` file and a `.manifest` containing the table
count, database size, dump size, timestamp, and retention setting.

## Verify

```bash
cd /opt/intelliconf/backups/database/latest
sha256sum -c intelliconf-*.sql.gz.sha256
gzip -t intelliconf-*.sql.gz
systemctl status intelliconf-db-backup.timer --no-pager
journalctl -u intelliconf-db-backup.service -n 50 --no-pager
```

## Restore into an isolated database first

```bash
backup=/opt/intelliconf/backups/database/latest/intelliconf-YYYYMMDD-HHMMSS.sql.gz
mariadb -e 'CREATE DATABASE intelliconf_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
gzip -dc "$backup" | mariadb intelliconf_restore
mariadb -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='intelliconf_restore';"
```

Only replace the production database after the isolated restore, row-count
comparison, application maintenance window, and a fresh pre-restore backup.
