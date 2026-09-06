CREATE TABLE `conf_member` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `conf_id` bigint NOT NULL COMMENT '具体会议ID',
    `contact_id` bigint NOT NULL COMMENT '关联contact_pool中的ID',
    `role` varchar(50) NOT NULL COMMENT '在此会议中的身份(如: AUTHOR, REVIEWER)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_conf_role` (`conf_id`, `role`)
) ENGINE=InnoDB COMMENT='会议成员及身份关联表';