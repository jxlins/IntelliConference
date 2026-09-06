CREATE TABLE `location`  (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `confer_id` bigint NOT NULL COMMENT '关联会议ID',
    `province` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '省名称',
    `city` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '市名称',
    `country` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '国家标识',
    `address` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '详细地址',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_confer_id`(`confer_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;