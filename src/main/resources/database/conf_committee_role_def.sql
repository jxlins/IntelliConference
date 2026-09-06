CREATE TABLE conf_committee_role_def (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '委员会角色定义ID',

    role_code VARCHAR(100) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',

    committee_type VARCHAR(100) NOT NULL COMMENT '委员会类型编码',
    committee_name VARCHAR(100) NOT NULL COMMENT '委员会名称',

    role_desc TEXT DEFAULT NULL COMMENT '角色说明',

    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否关键角色：1是，0否',
    can_assign_task TINYINT NOT NULL DEFAULT 1 COMMENT '是否可作为任务负责人：1是，0否',

    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),

    UNIQUE KEY uk_role_code (role_code),
    KEY idx_committee_type (committee_type),
    KEY idx_status (status),
    KEY idx_sort_order (sort_order),

    CONSTRAINT chk_committee_role_required
        CHECK (is_required IN (0, 1)),

    CONSTRAINT chk_committee_role_assign_task
        CHECK (can_assign_task IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议委员会角色定义表';