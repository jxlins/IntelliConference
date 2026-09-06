CREATE TABLE `conf_contact_pool` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `owner_org_id` bigint NOT NULL COMMENT '所属组织者ID(关联sys_user或其机构)',
    `name` varchar(100) NOT NULL COMMENT '姓名',
    `email` varchar(150) NOT NULL COMMENT '邮箱',
    `institution` varchar(255) COMMENT '单位',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_org_email` (`owner_org_id`, `email`)
) ENGINE=InnoDB COMMENT='组织者名下的联系人总库';