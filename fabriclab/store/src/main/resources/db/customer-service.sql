-- FABRICLAB 客服模块扩展表（咨询会话 / 消息）
-- 课程 01-schema.sql 的七张业务表结构保持不变；本脚本只新增两张扩展表。
-- 可重复执行：建表带 IF NOT EXISTS，演示数据按编号与消息内容判重。
-- 在已按 db/README.md 导入 01-schema.sql + seed-data.sql 之后执行。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+08:00';
USE MyPetStore;

-- 1. 咨询会话：状态 OPEN 待回复 / REPLIED 已回复 / CLOSED 已关闭。
--    关联订单号与款号只作展示快照，不参与订单状态、金额或售后流程。
CREATE TABLE IF NOT EXISTS service_thread (
    id BIGINT NOT NULL AUTO_INCREMENT,
    thread_no VARCHAR(32) NOT NULL COMMENT '服务端生成的咨询编号，创建后不变',
    user_id BIGINT NOT NULL COMMENT '发起咨询的会员',
    category VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SIZING / ORDER / FABRIC / OTHER',
    subject VARCHAR(120) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN',
    related_order_no VARCHAR(40) NULL COMMENT '选填：本人完整订单号，仅作展示',
    related_sku VARCHAR(40) NULL COMMENT '选填：咨询所涉款号，仅作展示',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间，列表按此倒序',
    first_replied_at DATETIME NULL COMMENT '客服首次回复时间',
    closed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_thread_no (thread_no),
    KEY idx_service_thread_user (user_id, last_activity_at, id),
    KEY idx_service_thread_status (status, last_activity_at, id),
    CONSTRAINT fk_service_thread_user FOREIGN KEY (user_id) REFERENCES store_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_service_thread_no CHECK (CHAR_LENGTH(TRIM(thread_no)) > 0),
    CONSTRAINT ck_service_thread_subject CHECK (CHAR_LENGTH(TRIM(subject)) > 0),
    CONSTRAINT ck_service_thread_category CHECK (category IN ('SIZING', 'ORDER', 'FABRIC', 'OTHER')),
    CONSTRAINT ck_service_thread_status CHECK (status IN ('OPEN', 'REPLIED', 'CLOSED')),
    CONSTRAINT ck_service_thread_closed CHECK (
        (status = 'CLOSED' AND closed_at IS NOT NULL)
        OR (status <> 'CLOSED' AND closed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选购咨询会话';

-- 2. 会话消息：会员与客服的消息按 id 升序组成完整对话，正文由模板转义输出。
CREATE TABLE IF NOT EXISTS service_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    sender VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CUSTOMER 或 ADMIN',
    author_id BIGINT NOT NULL COMMENT '发信账号，两端共用 store_user',
    author_name VARCHAR(50) NOT NULL COMMENT '发信时的显示名快照',
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_service_message_thread (thread_id, id),
    CONSTRAINT fk_service_message_thread FOREIGN KEY (thread_id) REFERENCES service_thread (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_service_message_sender CHECK (sender IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_service_message_author CHECK (CHAR_LENGTH(TRIM(author_name)) > 0),
    CONSTRAINT ck_service_message_body CHECK (CHAR_LENGTH(TRIM(body)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='咨询会话消息';

-- ── 演示数据（本地教学用，全部为真实库记录，可重复执行） ────────────────────
-- 覆盖待回复 / 已回复 / 已关闭 / 用户追问后重回待回复四种实际状态。
INSERT INTO service_thread
    (thread_no, user_id, category, subject, status, related_order_no, related_sku,
     created_at, last_activity_at, first_replied_at, closed_at)
SELECT x.thread_no, u.id, x.category, x.subject, x.status, x.order_no, x.sku,
       DATE_SUB(NOW(), INTERVAL x.age HOUR), DATE_SUB(NOW(), INTERVAL x.last HOUR),
       x.replied, x.closed
FROM (SELECT 'FL-S-DEMO-0001' AS thread_no, 'alice' AS username, 'SIZING' AS category,
             '高领衫的尺码该怎么选' AS subject, 'OPEN' AS status, NULL AS order_no,
             'FL-HM-01' AS sku, 26 AS age, 26 AS last,
             NULL AS replied, NULL AS closed
      UNION ALL SELECT 'FL-S-DEMO-0002', 'alice', 'FABRIC', '山羊绒起球与洗护', 'REPLIED',
             NULL, 'FL-CM-01', 40, 21, DATE_SUB(NOW(), INTERVAL 21 HOUR), NULL
      UNION ALL SELECT 'FL-S-DEMO-0003', 'bob', 'ORDER', '模拟支付后订单状态在哪看', 'CLOSED',
             'DEMO-202611-BO-02', NULL, 72, 50, DATE_SUB(NOW(), INTERVAL 52 HOUR),
             DATE_SUB(NOW(), INTERVAL 50 HOUR)
      UNION ALL SELECT 'FL-S-DEMO-0004', 'bob', 'OTHER', '门店试穿需要预约吗', 'OPEN',
             NULL, NULL, 30, 2, DATE_SUB(NOW(), INTERVAL 28 HOUR), NULL) x
JOIN store_user u ON u.username = x.username
WHERE NOT EXISTS (SELECT 1 FROM (SELECT thread_no FROM service_thread) t WHERE t.thread_no = x.thread_no);

INSERT INTO service_message (thread_id, sender, author_id, author_name, body, created_at)
SELECT t.id, m.sender, u.id, m.author_name, m.body, DATE_SUB(NOW(), INTERVAL m.age HOUR)
FROM (SELECT 'FL-S-DEMO-0001' AS no, 'CUSTOMER' AS sender, 'alice' AS username,
             '林晓' AS author_name, '平时穿 M，山羊绒高领衫想合身一点，选 M 还是 L？肩宽 44。' AS body, 26 AS age
      UNION ALL SELECT 'FL-S-DEMO-0002', 'CUSTOMER', 'alice', '林晓',
             '外套标注 100% 山羊绒，穿了两次袖口有一点起球，平时该怎么洗护？', 40
      UNION ALL SELECT 'FL-S-DEMO-0002', 'ADMIN', 'admin', '平台管理员',
             '山羊绒初期表面浮绒属正常现象，不建议频繁水洗：通风悬挂、用软毛刷顺向轻刷即可。局部污渍请送门店处理，整件干洗一年不超过两次。', 21
      UNION ALL SELECT 'FL-S-DEMO-0003', 'CUSTOMER', 'bob', '陈晨',
             '刚完成教学模拟支付，订单页还显示待支付吗？', 72
      UNION ALL SELECT 'FL-S-DEMO-0003', 'ADMIN', 'admin', '平台管理员',
             '完成模拟支付后订单状态会改为「待发货」，并写入 MOCK 凭证与支付时间；在「我的订单」按状态筛选即可查到。', 52
      UNION ALL SELECT 'FL-S-DEMO-0003', 'CUSTOMER', 'bob', '陈晨',
             '明白了，谢谢。', 50
      UNION ALL SELECT 'FL-S-DEMO-0004', 'CUSTOMER', 'bob', '陈晨',
             '周末想去静安门店试穿通勤系列，需要提前预约吗？', 30
      UNION ALL SELECT 'FL-S-DEMO-0004', 'ADMIN', 'admin', '平台管理员',
             '门店无需预约，营业时间 11:00—20:00；如需一次试穿三款以上，建议来电 021 6258 0031 预留试衣间。', 28
      UNION ALL SELECT 'FL-S-DEMO-0004', 'CUSTOMER', 'bob', '陈晨',
             '想试的款号是 FL-CM-01 和 FL-CM-03，能帮我确认店里有没有现货吗？', 2) m
JOIN service_thread t ON t.thread_no = m.no
JOIN store_user u ON u.username = m.username
WHERE NOT EXISTS (SELECT 1 FROM (SELECT thread_id, body FROM service_message) s
                  WHERE s.thread_id = t.id AND s.body = m.body)
-- 按会话、再由旧到新写入，保证自增 id 与对话时间顺序一致
ORDER BY m.no, m.age DESC;
