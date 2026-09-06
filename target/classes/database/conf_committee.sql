CREATE TABLE `conf_committee` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conf_id` bigint NOT NULL COMMENT '会议ID',
    `email` varchar(256) NOT NULL COMMENT '委员会成员邮箱',
    `name` varchar(256) DEFAULT NULL COMMENT '委员会成员姓名',
    `institution` varchar(256) DEFAULT NULL COMMENT '委员会成员单位',
    `role` varchar(64) NOT NULL COMMENT '委员会角色 CHAIR/REVIEWER',
    `invite_status` varchar(32) NOT NULL DEFAULT 'INVITED' COMMENT '邀请状态 INVITED/ACCEPTED/DECLINED',
    `access_token` varchar(64) DEFAULT NULL COMMENT '门户访问令牌(UUID)',
    `token_expire_time` datetime DEFAULT NULL COMMENT '令牌过期时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` tinyint DEFAULT 0 COMMENT '删除标识 0-有效 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conf_email` (`conf_id`,`email`),
    UNIQUE KEY `uk_access_token` (`access_token`),
    KEY `idx_conf_role` (`conf_id`,`role`),
    KEY `idx_conf_status` (`conf_id`,`invite_status`)
) ENGINE=InnoDB COMMENT='会议委员会成员表';
