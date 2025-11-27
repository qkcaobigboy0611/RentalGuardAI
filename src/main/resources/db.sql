CREATE TABLE `channel`
(
    `id`          int unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `creator_id`  char(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id',
    `peer_id`     char(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id',
    `type`        tinyint                                                       DEFAULT '0' COMMENT '类型',
    `meta`        varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '信息',
    `offset`      int                                                       NOT NULL COMMENT 'offset',
    `latest`      int unsigned DEFAULT NULL COMMENT '最新的消息',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP (3) COMMENT '创建时间',
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP (3) ON UPDATE CURRENT_TIMESTAMP (3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY           `creator_id` (`creator_id`),
    KEY           `peer_id` (`peer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=267889 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='channel列表';

CREATE TABLE `fraud_detection_record`
(
    `id`                     int unsigned NOT NULL AUTO_INCREMENT,
    `user_id`                char(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci     NOT NULL COMMENT '用户ID',
    `channel_id`             bigint                                                        NOT NULL COMMENT '聊天频道ID',
    `trigger_sensitive_word` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发的敏感词',
    `trigger_message`        text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '触发的消息内容',
    `chat_context`           json                                                                   DEFAULT NULL COMMENT '聊天上下文JSON',
    `ai_analysis_result`     json                                                                   DEFAULT NULL COMMENT 'AI分析结果JSON',
    `risk_score`             decimal(3, 2)                                                          DEFAULT NULL COMMENT '风险评分 0.00-1.00',
    `is_fraud`               tinyint                                                                DEFAULT NULL COMMENT '是否判定为欺诈 0-否 1-是',
    `fraud_type`             varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci           DEFAULT NULL COMMENT '欺诈类型',
    `action_taken`           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci           DEFAULT NULL COMMENT '执行的动作',
    `ai_cost_time`           int                                                                    DEFAULT NULL COMMENT 'AI分析耗时(毫秒)',
    `create_time`            timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                      `idx_user_id` (`user_id`),
    KEY                      `idx_channel_id` (`channel_id`),
    KEY                      `idx_create_time` (`create_time`),
    KEY                      `idx_fraud` (`is_fraud`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='欺诈检测记录表';

CREATE TABLE `fraud_training_case`
(
    `id`               int unsigned NOT NULL AUTO_INCREMENT,
    `chat_content`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '聊天内容',
    `is_fraud`         tinyint                                                      DEFAULT NULL COMMENT '是否欺诈 0-否 1-是',
    `fraud_type`       varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '欺诈类型',
    `source`           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'manual' COMMENT '来源：manual-人工标注 auto-AI判断',
    `confidence_score` decimal(3, 2)                                                DEFAULT NULL COMMENT '置信度 0.00-1.00',
    `description`      text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '备注说明',
    `create_time`      datetime                                                     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime                                                     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `vector`           float                                                        DEFAULT NULL COMMENT '向量',
    PRIMARY KEY (`id`),
    KEY                `idx_fraud_type` (`fraud_type`),
    KEY                `idx_source` (`source`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='欺诈训练案例表';


CREATE TABLE `message`
(
    `id`          int unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `channel_id`  int unsigned DEFAULT NULL COMMENT 'channelId',
    `creator_id`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  DEFAULT NULL COMMENT '用户id',
    `type`        tinyint                                                        DEFAULT '0' COMMENT '消息类型',
    `payload`     varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'payload',
    `offset`      int unsigned DEFAULT NULL COMMENT 'offset',
    `create_time` datetime                                                       DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_user_channel` (`channel_id`,`offset`)
) ENGINE=InnoDB AUTO_INCREMENT=846759 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='message列表';


CREATE TABLE `user`
(
    `id`        int unsigned NOT NULL AUTO_INCREMENT,
    `user_id`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '唯一用户id, 18位UUID',
    `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  DEFAULT NULL COMMENT '昵称',
    `phone`     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
    `ip`        varchar(255) COLLATE utf8mb4_general_ci                       DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
