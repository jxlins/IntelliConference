CREATE TABLE `sys_user`  (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `username` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
    `password` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
    `email` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号码',
    `real_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '真实姓名',
    `delete_time` bigint NULL DEFAULT NULL COMMENT '注销时间戳',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `del_flag` tinyint(1) NULL DEFAULT NULL COMMENT '删除标识 0：未删除 1：已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;