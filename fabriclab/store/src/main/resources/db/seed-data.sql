-- FabricLab-Atelier 演示种子数据（仅业务数据写入，不含建表DDL）
-- 前置条件：先在空库执行 01-schema.sql 建表，再整体执行本脚本。
-- 演示口令统一为 Passw0rd!2026；BCrypt cost=10，每个账号独立盐。
-- 仅为本地教学账号，不可作为真实部署凭据。
-- 商品图取自公开网页的真实商品/面料摄影（非 AI 生成），逐图来源见同目录 README.md；
-- 仅用于课程作业演示，不表示同图商品的实际品相。logo 为程序自绘，无第三方版权。
-- 覆盖场景：库存1 / 上架售罄(0) / 下架(DRAFT) / 无相册回退封面 /
-- 平铺-上身-细节相册 / 五状态订单各2张 / 6张MOCK支付 / 两张历史价快照差异 /
-- 取消订单不净扣库存。本脚本面向空库一次性导入；
-- 已有同名账号/SKU/订单号时会在主键或唯一键处报错停止，请勿对已导入库重复执行。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+08:00';
USE MyPetStore;

START TRANSACTION;

-- 演示账号：alice/bob为顾客，disabled为禁用样例，admin为管理员。
INSERT INTO store_user (id, username, password_hash, display_name, role, status, email, phone) VALUES
    (1, 'alice', '$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76', '林晓', 'CUSTOMER', 'ENABLED', 'alice@example.test', NULL),
    (2, 'bob', '$2a$10$CdUEndzUHDMgTMYS.s01Be42kq7BcgdKg3IsNaZ/C5SXn59kuPZh.', '陈晨', 'CUSTOMER', 'ENABLED', 'bob@example.test', NULL),
    (3, 'disabled', '$2a$10$cqgiwnULpeIWWGp9UAN1QuhMn1CSvXti4k4GFfJ6pk.frDVYVmaT.', '禁用教学账号', 'CUSTOMER', 'DISABLED', 'disabled@example.test', NULL),
    (4, 'admin', '$2a$10$7XejrDkqfmr8TUPOmqDcrOFlMr4VNbjBKAdX4ItR4rf9F/uzkdMuu', '工坊管理员', 'ADMIN', 'ENABLED', 'admin@example.test', NULL);

-- 一级分类先插入，二级分类后插入；不同一级下允许同名二级。
INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
    (1, 'COMMUTE', NULL, '通勤出行', 10),
    (2, 'LEISURE', NULL, '休闲生活', 20),
    (3, 'OUTDOOR', NULL, '户外运动', 30),
    (4, 'OCCASION', NULL, '礼服场合', 40),
    (5, 'HOME', NULL, '居家舒适', 50);

INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
    (101, 'COMMUTE_GAR', 1, '通勤成衣', 10),
    (102, 'COMMUTE_ACC', 1, '通勤配饰', 20),
    (201, 'LEISURE_GAR', 2, '休闲服饰', 10),
    (202, 'LEISURE_ACC', 2, '休闲配饰', 20),
    (301, 'OUTDOOR_GAR', 3, '户外服饰', 10),
    (302, 'OUTDOOR_ACC', 3, '户外配件', 20),
    (401, 'OCCASION_GAR', 4, '礼服成衣', 10),
    (402, 'OCCASION_ACC', 4, '礼服配饰', 20),
    (501, 'HOME_GAR', 5, '居家服装', 10),
    (502, 'HOME_ACC', 5, '居家用品', 20);

-- 60款：10个二级分类各6款；覆盖 6下架、3上架售罄、3库存1。
-- 尺码与配色体现在名称中（单SKU口径）；面料档案为多行纯文本。
INSERT INTO product (id, sku, category_id, name, summary, description, cover_image,
    price, stock, status, sort_order, version)
SELECT t.id, t.sku, c.id, t.name, t.summary, t.description, t.cover_image,
       t.price, t.stock, t.status, t.sort_order, 0
FROM (
    SELECT 1 AS id, 'FL-CM-01' AS sku, 'COMMUTE_GAR' AS category_code, '云感直筒西装外套' AS name, '精纺羊毛西装外套，通勤首选基础款。' AS summary, '云感直筒西装外套。面料：精纺羊毛混纺
克重：320g/m²
版型：H型直筒
工艺：半麻衬手工纳驳头
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 429.00 AS price, 25 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 2 AS id, 'FL-CM-02' AS sku, 'COMMUTE_GAR' AS category_code, '轻氧通勤衬衫' AS name, '高支棉衬衫，领口挺括不易变形。' AS summary, '轻氧通勤衬衫。面料：高支精梳棉
克重：140g/m²
版型：修身微收腰
工艺：免烫防皱压光
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 199.00 AS price, 40 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 3 AS id, 'FL-CM-03' AS sku, 'COMMUTE_GAR' AS category_code, '挺括九分烟管裤' AS name, '弹力面料九分裤，久坐不勒腿。' AS summary, '挺括九分烟管裤。面料：棉涤弹力斜纹布
克重：260g/m²
版型：直筒九分
工艺：高温定型压线
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 259.00 AS price, 30 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 4 AS id, 'FL-CM-04' AS sku, 'COMMUTE_GAR' AS category_code, '双面呢通勤大衣' AS name, '双面呢大衣，无里布轻量保暖。' AS summary, '双面呢通勤大衣。面料：100%羊毛双面呢
克重：580g/m²
版型：茧型宽松
工艺：手工对缝无里布
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 699.00 AS price, 12 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 5 AS id, 'FL-CM-05' AS sku, 'COMMUTE_GAR' AS category_code, '通勤褶裥半身裙' AS name, '下架教学样例，供后台上架练习。' AS summary, '通勤褶裥半身裙。面料：三醋酸混纺
克重：210g/m²
版型：A字中长
工艺：风琴褶机压定型
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 219.00 AS price, 8 AS stock, 'DRAFT' AS status, 50 AS sort_order
    UNION ALL
    SELECT 6 AS id, 'FL-CM-06' AS sku, 'COMMUTE_GAR' AS category_code, '通勤弹力打底衫' AS name, '上架零库存样例，用于售罄提示。' AS summary, '通勤弹力打底衫。面料：莫代尔氨纶汗布
克重：180g/m²
版型：贴身修身
工艺：三针五线包边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-01/1.jpg' AS cover_image, 99.00 AS price, 0 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 7 AS id, 'FL-CM-07' AS sku, 'COMMUTE_ACC' AS category_code, '极简通勤托特包' AS name, '头层牛皮托特包，可装14寸笔记本。' AS summary, '极简通勤托特包。面料：头层牛皮
克重：约900g
版型：立挺方型
工艺：手工油边缝线
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 359.00 AS price, 18 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 8 AS id, 'FL-CM-08' AS sku, 'COMMUTE_ACC' AS category_code, '通勤真皮乐福鞋' AS name, '软底乐福鞋，久走不累。' AS summary, '通勤真皮乐福鞋。面料：头层牛皮
克重：约350g
版型：标准楦型
工艺：固特异沿条缝制
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 399.00 AS price, 16 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 9 AS id, 'FL-CM-09' AS sku, 'COMMUTE_ACC' AS category_code, '通勤哑光细腰带' AS name, '哑光合金扣皮带，低调百搭。' AS summary, '通勤哑光细腰带。面料：二层牛皮+锌合金
克重：约120g
版型：2.0cm窄版
工艺：激光镭射标识
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 79.00 AS price, 45 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 10 AS id, 'FL-CM-10' AS sku, 'COMMUTE_ACC' AS category_code, '通勤真丝丝巾' AS name, '桑蚕丝小方巾，拎包即走。' AS summary, '通勤真丝丝巾。面料：100%桑蚕丝
克重：16姆米
版型：65cm方巾
工艺：手工卷边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 129.00 AS price, 22 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 11 AS id, 'FL-CM-11' AS sku, 'COMMUTE_ACC' AS category_code, '通勤防泼水折叠伞' AS name, '五折伞轻便收纳，防泼水面料。' AS summary, '通勤防泼水折叠伞。面料：碰击布+铝合金
克重：约180g
版型：五折迷你
工艺：UV50+黑胶涂层
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 59.00 AS price, 35 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 12 AS id, 'FL-CM-12' AS sku, 'COMMUTE_ACC' AS category_code, '通勤磨砂卡包' AS name, '磨砂牛皮卡包，八卡位轻收纳。' AS summary, '通勤磨砂卡包。面料：磨砂牛皮
克重：约60g
版型：竖式八卡位
工艺：撞色包边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-CM-07/1.jpg' AS cover_image, 69.00 AS price, 50 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 13 AS id, 'FL-LS-01' AS sku, 'LEISURE_GAR' AS category_code, '云朵卫衣' AS name, '重磅毛圈卫衣，落肩慵懒版型。' AS summary, '云朵卫衣。面料：纯棉重磅毛圈布
克重：380g/m²
版型：落肩宽松
工艺：水洗预缩三线拷边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 189.00 AS price, 38 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 14 AS id, 'FL-LS-02' AS sku, 'LEISURE_GAR' AS category_code, '街头宽松T恤' AS name, '厚实不透光，宽松街头剪裁。' AS summary, '街头宽松T恤。面料：精梳棉双纱
克重：260g/m²
版型：OS落肩
工艺：洗水做旧印花
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 89.00 AS price, 60 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 15 AS id, 'FL-LS-03' AS sku, 'LEISURE_GAR' AS category_code, '复古水洗牛仔外套' AS name, '低库存样例，仅剩最后一件。' AS summary, '复古水洗牛仔外套。面料：全棉水洗牛仔
克重：340g/m²
版型：oversize
工艺：酵素洗磨白
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 329.00 AS price, 1 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 16 AS id, 'FL-LS-04' AS sku, 'LEISURE_GAR' AS category_code, '弹力运动短裤' AS name, '速干弹力短裤，运动日常两相宜。' AS summary, '弹力运动短裤。面料：锦氨速干面料
克重：150g/m²
版型：五分宽松
工艺：无侧缝压胶
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 69.00 AS price, 55 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 17 AS id, 'FL-LS-05' AS sku, 'LEISURE_GAR' AS category_code, '宽松直筒牛仔裤' AS name, '微弹直筒版型，显腿长不挑人。' AS summary, '宽松直筒牛仔裤。面料：棉涤弹力牛仔
克重：360g/m²
版型：高腰直筒
工艺：赤耳原色未洗
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 279.00 AS price, 24 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 18 AS id, 'FL-LS-06' AS sku, 'LEISURE_GAR' AS category_code, '慵懒针织开衫' AS name, '粗针羊毛混纺开衫，软糯亲肤。' AS summary, '慵懒针织开衫。面料：羊毛腈纶混纺纱
克重：420g/m²
版型：中长宽松
工艺：手缝贝母扣
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-01/1.jpg' AS cover_image, 239.00 AS price, 20 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 19 AS id, 'FL-LS-07' AS sku, 'LEISURE_ACC' AS category_code, '街拍帆布斜挎包' AS name, '加厚帆布斜挎包，耐磨耐装。' AS summary, '街拍帆布斜挎包。面料：16安加厚帆布
克重：约420g
版型：横式小方包
工艺：四合扣加磁吸双开
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 119.00 AS price, 26 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 20 AS id, 'FL-LS-08' AS sku, 'LEISURE_ACC' AS category_code, '休闲百搭板鞋' AS name, '硫化鞋底软弹，全棉鞋面。' AS summary, '休闲百搭板鞋。面料：帆布+天然橡胶
克重：约300g
版型：标准楦型
工艺：硫化一体成型
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 259.00 AS price, 30 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 21 AS id, 'FL-LS-09' AS sku, 'LEISURE_ACC' AS category_code, '撞色棒球帽' AS name, '可调节帽围，硬顶有型。' AS summary, '撞色棒球帽。面料：全棉斜纹布
克重：约110g
版型：六片硬顶
工艺：立体刺绣
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 59.00 AS price, 48 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 22 AS id, 'FL-LS-10' AS sku, 'LEISURE_ACC' AS category_code, '城市轻便腰包' AS name, '下架教学样例，供筛选与上架练习。' AS summary, '城市轻便腰包。面料：尼龙牛津布
克重：约90g
版型：单仓斜挎
工艺：防水涂层拉链
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 89.00 AS price, 15 AS stock, 'DRAFT' AS status, 40 AS sort_order
    UNION ALL
    SELECT 23 AS id, 'FL-LS-11' AS sku, 'LEISURE_ACC' AS category_code, '街头印花渔夫帽' AS name, '两面戴渔夫帽，印花设计。' AS summary, '街头印花渔夫帽。面料：全棉平纹布
克重：约95g
版型：双面两用
工艺：数码印花
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 49.00 AS price, 40 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 24 AS id, 'FL-LS-12' AS sku, 'LEISURE_ACC' AS category_code, '休闲编织手环' AS name, '手工编织手环，多色可选设计。' AS summary, '休闲编织手环。面料：尼龙编织绳
克重：约20g
版型：可调节
工艺：手工密编
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-LS-07/1.jpg' AS cover_image, 29.00 AS price, 65 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 25 AS id, 'FL-OD-01' AS sku, 'OUTDOOR_GAR' AS category_code, '三层防风冲锋衣' AS name, '三合一冲锋衣，防风防泼水。' AS summary, '三层防风冲锋衣。面料：锦纶+TPU复合
克重：280g/m²
版型：户外机能版型
工艺：压胶防风门襟
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 899.00 AS price, 35 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 26 AS id, 'FL-OD-02' AS sku, 'OUTDOOR_GAR' AS category_code, '轻量防晒皮肤衣' AS name, 'UPF50+皮肤衣，轻至百克。' AS summary, '轻量防晒皮肤衣。面料：锦纶四面弹
克重：95g/m²
版型：修身连帽
工艺：UPF50+涂层
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 299.00 AS price, 35 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 27 AS id, 'FL-OD-03' AS sku, 'OUTDOOR_GAR' AS category_code, '抓绒保暖中层衣' AS name, '下架教学样例，供后台上架练习。' AS summary, '抓绒保暖中层衣。面料：摇粒绒
克重：300g/m²
版型：套头宽松
工艺：反面磨毛防静电
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 269.00 AS price, 18 AS stock, 'DRAFT' AS status, 30 AS sort_order
    UNION ALL
    SELECT 28 AS id, 'FL-OD-04' AS sku, 'OUTDOOR_GAR' AS category_code, '速干徒步长裤' AS name, '四面弹速干裤，耐磨防刮。' AS summary, '速干徒步长裤。面料：锦氨速干格子布
克重：190g/m²
版型：修身束脚
工艺：防泼水DWR处理
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 329.00 AS price, 28 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 29 AS id, 'FL-OD-05' AS sku, 'OUTDOOR_GAR' AS category_code, '户外轻量雨衣' AS name, '上架零库存样例，用于售罄提示。' AS summary, '户外轻量雨衣。面料：涤纶+PU涂层
克重：220g/m²
版型：宽松罩式
工艺：热压无缝防水
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 499.00 AS price, 0 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 30 AS id, 'FL-OD-06' AS sku, 'OUTDOOR_GAR' AS category_code, '弹力登山短裤' AS name, '耐磨弹力短裤，夏季徒步首选。' AS summary, '弹力登山短裤。面料：锦纶四面弹
克重：170g/m²
版型：五分束口
工艺：激光冲孔透气
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-01/1.jpg' AS cover_image, 159.00 AS price, 33 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 31 AS id, 'FL-OD-07' AS sku, 'OUTDOOR_ACC' AS category_code, '户外登山双肩包' AS name, '38L登山包，独立防水仓设计。' AS summary, '户外登山双肩包。面料：尼龙杜邦复合布
克重：约1200g
版型：38L背负
工艺：EVA缓震背板
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 459.00 AS price, 14 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 32 AS id, 'FL-OD-08' AS sku, 'OUTDOOR_ACC' AS category_code, '防滑溯溪鞋' AS name, '排水溯溪鞋，湿滑路面防滑。' AS summary, '防滑溯溪鞋。面料：合成革+橡胶
克重：约600g
版型：包脚防脱
工艺：排水孔网面
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 379.00 AS price, 20 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 33 AS id, 'FL-OD-09' AS sku, 'OUTDOOR_ACC' AS category_code, '户外遮阳帽' AS name, '大檐遮阳帽，可收纳变形。' AS summary, '户外遮阳帽。面料：锦纶空顶布
克重：约90g
版型：大檐可折叠
工艺：UV50+织带
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 99.00 AS price, 52 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 34 AS id, 'FL-OD-10' AS sku, 'OUTDOOR_ACC' AS category_code, '保温运动水壶' AS name, '12小时保温水壶，食品级内胆。' AS summary, '保温运动水壶。面料：316不锈钢
克重：约380g
版型：500ml直身
工艺：真空镀铜保温
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 129.00 AS price, 44 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 35 AS id, 'FL-OD-11' AS sku, 'OUTDOOR_ACC' AS category_code, '户外折叠登山杖' AS name, '五节折叠杖，碳纤维轻量。' AS summary, '户外折叠登山杖。面料：碳纤维
克重：约210g
版型：五节可调
工艺：内锁快调系统
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 139.00 AS price, 25 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 36 AS id, 'FL-OD-12' AS sku, 'OUTDOOR_ACC' AS category_code, '防晒冰袖套' AS name, '凉感冰袖，骑行徒步通用。' AS summary, '防晒冰袖套。面料：冰感锦氨
克重：约40g
版型：弹力贴身
工艺：凉感助剂织入
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OD-07/1.jpg' AS cover_image, 39.00 AS price, 70 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 37 AS id, 'FL-OC-01' AS sku, 'OCCASION_GAR' AS category_code, '香槟缎面晚礼服' AS name, '低库存样例，仅剩最后一件。' AS summary, '香槟缎面晚礼服。面料：醋酸缎面
克重：190g/m²
版型：鱼骨收腰长裙
工艺：手工钉珠锁边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 1299.00 AS price, 1 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 38 AS id, 'FL-OC-02' AS sku, 'OCCASION_GAR' AS category_code, '赫本风小黑裙' AS name, '经典小黑裙，通勤宴会两相宜。' AS summary, '赫本风小黑裙。面料：羊毛混纺精纺呢
克重：340g/m²
版型：X型收腰
工艺：全手工归拔定型
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 899.00 AS price, 9 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 39 AS id, 'FL-OC-03' AS sku, 'OCCASION_GAR' AS category_code, '法式碎花连衣裙' AS name, '雪纺碎花裙，度假日常都好穿。' AS summary, '法式碎花连衣裙。面料：涤纶雪纺
克重：120g/m²
版型：A字长裙
工艺：手工压褶包边
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 599.00 AS price, 14 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 40 AS id, 'FL-OC-04' AS sku, 'OCCASION_GAR' AS category_code, '丝绒西装礼服套装' AS name, '丝绒西服套装，酒会首选。' AS summary, '丝绒西装礼服套装。面料：天鹅绒
克重：300g/m²
版型：英式修身
工艺：丝绒定向剪裁
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 1099.00 AS price, 6 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 41 AS id, 'FL-OC-05' AS sku, 'OCCASION_GAR' AS category_code, '亮片宴会礼服' AS name, '重手工亮片礼服，宴会焦点。' AS summary, '亮片宴会礼服。面料：网纱+手工亮片
克重：约650g
版型：贴身鱼尾
工艺：手工缝珠亮片
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 1499.00 AS price, 7 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 42 AS id, 'FL-OC-06' AS sku, 'OCCASION_GAR' AS category_code, '轻纱伴娘长裙' AS name, '柔雾轻纱长裙，伴娘聚会皆宜。' AS summary, '轻纱伴娘长裙。面料：网纱+缎面里布
克重：110g/m²
版型：高腰长裙
工艺：多层软纱叠压
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-01/1.jpg' AS cover_image, 759.00 AS price, 10 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 43 AS id, 'FL-OC-07' AS sku, 'OCCASION_ACC' AS category_code, '珍珠缀饰手拿包' AS name, '珍珠缀饰手拿包，晚宴必备。' AS summary, '珍珠缀饰手拿包。面料：缎面+仿珍珠
克重：约280g
版型：晚宴小方包
工艺：手工钉珠
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 299.00 AS price, 12 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 44 AS id, 'FL-OC-08' AS sku, 'OCCASION_ACC' AS category_code, '绸缎绅士领结' AS name, '下架教学样例，供后台上架练习。' AS summary, '绸缎绅士领结。面料：真丝缎带
克重：约45g
版型：预打结式
工艺：手工折边成型
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 89.00 AS price, 20 AS stock, 'DRAFT' AS status, 20 AS sort_order
    UNION ALL
    SELECT 45 AS id, 'FL-OC-09' AS sku, 'OCCASION_ACC' AS category_code, '水晶晚宴耳饰' AS name, '锆石水晶耳饰，提亮整体造型。' AS summary, '水晶晚宴耳饰。面料：锆石+925银针
克重：约8g
版型：垂坠款
工艺：微镶工艺
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 149.00 AS price, 18 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 46 AS id, 'FL-OC-10' AS sku, 'OCCASION_ACC' AS category_code, '礼服漆皮皮鞋' AS name, '漆面礼服皮鞋，正装首选。' AS summary, '礼服漆皮皮鞋。面料：漆皮牛皮
克重：约380g
版型：尖头修长楦
工艺：手工抛光上漆
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 699.00 AS price, 8 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 47 AS id, 'FL-OC-11' AS sku, 'OCCASION_ACC' AS category_code, '晚宴珠链披肩' AS name, '上架零库存样例，用于售罄提示。' AS summary, '晚宴珠链披肩。面料：人造珠链网纱
克重：约200g
版型：三角披肩
工艺：手工串珠
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 259.00 AS price, 0 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 48 AS id, 'FL-OC-12' AS sku, 'OCCASION_ACC' AS category_code, '珠光发饰礼盒' AS name, '三件套发饰礼盒，伴手礼首选。' AS summary, '珠光发饰礼盒。面料：亚克力+合金
克重：约60g
版型：三件套
工艺：珠光电镀
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-OC-07/1.jpg' AS cover_image, 119.00 AS price, 22 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 49 AS id, 'FL-HM-01' AS sku, 'HOME_GAR' AS category_code, '素色棉质睡衣套装' AS name, '精梳棉睡衣套装，居家舒适首选。' AS summary, '素色棉质睡衣套装。面料：全棉平纹布
克重：160g/m²
版型：宽松开衫
工艺：植物染色水洗
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 199.00 AS price, 30 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 50 AS id, 'FL-HM-02' AS sku, 'HOME_GAR' AS category_code, '珊瑚绒家居袍' AS name, '下架教学样例，供后台上架练习。' AS summary, '珊瑚绒家居袍。面料：珊瑚绒
克重：280g/m²
版型：长款袍式
工艺：双面拉毛
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 259.00 AS price, 16 AS stock, 'DRAFT' AS status, 20 AS sort_order
    UNION ALL
    SELECT 51 AS id, 'FL-HM-03' AS sku, 'HOME_GAR' AS category_code, '云柔家居短裤' AS name, '莫代尔短裤，睡眠凉感舒适。' AS summary, '云柔家居短裤。面料：莫代尔棉
克重：140g/m²
版型：宽松短裤
工艺：无骨缝制
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 79.00 AS price, 36 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 52 AS id, 'FL-HM-04' AS sku, 'HOME_GAR' AS category_code, '软糯家居袜三双装' AS name, '加厚毛圈袜口，三双装居家袜。' AS summary, '软糯家居袜三双装。面料：精梳棉毛圈
克重：约70g/双
版型：中筒均码
工艺：无骨对目缝头
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 39.00 AS price, 90 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 53 AS id, 'FL-HM-05' AS sku, 'HOME_GAR' AS category_code, '纯棉圆领家居T' AS name, '基础款家居T，百搭透气。' AS summary, '纯棉圆领家居T。面料：纯棉汗布
克重：200g/m²
版型：宽松圆领
工艺：领口双压线
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 69.00 AS price, 44 AS stock, 'ON_SALE' AS status, 50 AS sort_order
    UNION ALL
    SELECT 54 AS id, 'FL-HM-06' AS sku, 'HOME_GAR' AS category_code, '轻绒保暖家居裤' AS name, '低库存样例，仅剩最后一条。' AS summary, '轻绒保暖家居裤。面料：德绒发热纤维
克重：240g/m²
版型：束脚宽松
工艺：内里磨绒
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-01/1.jpg' AS cover_image, 129.00 AS price, 1 AS stock, 'ON_SALE' AS status, 60 AS sort_order
    UNION ALL
    SELECT 55 AS id, 'FL-HM-07' AS sku, 'HOME_ACC' AS category_code, '香薰蜡烛助眠套装' AS name, '大豆蜡香薰三件组，助眠舒缓。' AS summary, '香薰蜡烛助眠套装。面料：大豆蜡+精油
克重：约450g
版型：三罐套装
工艺：手工浇注
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 99.00 AS price, 26 AS stock, 'ON_SALE' AS status, 10 AS sort_order
    UNION ALL
    SELECT 56 AS id, 'FL-HM-08' AS sku, 'HOME_ACC' AS category_code, '云朵记忆棉拖鞋' AS name, '记忆棉底拖鞋，软弹静音。' AS summary, '云朵记忆棉拖鞋。面料：记忆棉+珊瑚绒
克重：约300g
版型：均码可裁
工艺：静音软底
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 89.00 AS price, 32 AS stock, 'ON_SALE' AS status, 20 AS sort_order
    UNION ALL
    SELECT 57 AS id, 'FL-HM-09' AS sku, 'HOME_ACC' AS category_code, '棉麻午睡眼罩' AS name, '棉麻眼罩，遮光透气。' AS summary, '棉麻午睡眼罩。面料：棉麻双层
克重：约30g
版型：可调绑带
工艺：填充荞麦内胆
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 39.00 AS price, 58 AS stock, 'ON_SALE' AS status, 30 AS sort_order
    UNION ALL
    SELECT 58 AS id, 'FL-HM-10' AS sku, 'HOME_ACC' AS category_code, '家居羊毛盖毯' AS name, '羊毛混纺盖毯，沙发午睡必备。' AS summary, '家居羊毛盖毯。面料：羊毛混纺
克重：约800g
版型：150x200cm
工艺：拉毛双面
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 189.00 AS price, 20 AS stock, 'ON_SALE' AS status, 40 AS sort_order
    UNION ALL
    SELECT 59 AS id, 'FL-HM-11' AS sku, 'HOME_ACC' AS category_code, '蒸汽眼罩舒缓装' AS name, '下架教学样例，供筛选与状态验证。' AS summary, '蒸汽眼罩舒缓装。面料：无纺布+发热体
克重：约30g/片
版型：单片发热
工艺：蒸汽自发热
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 49.00 AS price, 60 AS stock, 'DRAFT' AS status, 50 AS sort_order
    UNION ALL
    SELECT 60 AS id, 'FL-HM-12' AS sku, 'HOME_ACC' AS category_code, '陶瓷香氛摆件' AS name, '扩香陶瓷摆件，无火散香。' AS summary, '陶瓷香氛摆件。面料：白瓷+香氛片
克重：约220g
版型：立式摆件
工艺：釉下彩绘
所有名称、价格与库存仅用于课程演示，不构成真实销售建议。一个商品对应一个固定SKU，尺码与配色体现在商品名称中，不提供规格选项。' AS description, 'images/products/FL-HM-07/1.jpg' AS cover_image, 69.00 AS price, 15 AS stock, 'ON_SALE' AS status, 60 AS sort_order
) AS t
JOIN category c ON c.code = t.category_code;

-- 相册100条：每二级前两款4图（平铺/上身/面料/工艺），中两款1图示意照，后两款无相册。
INSERT INTO product_image (id, product_id, image_path, alt_text, sort_order)
SELECT seed.pi_id, p.id, seed.pi_path, seed.pi_alt, seed.pi_sort
FROM (
SELECT 1 AS pi_id, 'images/products/FL-CM-01/1.jpg' AS pi_path, '云感直筒西装外套·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-CM-01' AS pi_sku
UNION ALL
SELECT 2 AS pi_id, 'images/products/FL-CM-01/2.jpg' AS pi_path, '云感直筒西装外套·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-CM-01' AS pi_sku
UNION ALL
SELECT 3 AS pi_id, 'images/products/FL-CM-01/3.jpg' AS pi_path, '云感直筒西装外套·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-CM-01' AS pi_sku
UNION ALL
SELECT 4 AS pi_id, 'images/products/FL-CM-01/4.jpg' AS pi_path, '云感直筒西装外套·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-CM-01' AS pi_sku
UNION ALL
SELECT 5 AS pi_id, 'images/products/FL-CM-01/1.jpg' AS pi_path, '轻氧通勤衬衫·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-CM-02' AS pi_sku
UNION ALL
SELECT 6 AS pi_id, 'images/products/FL-CM-01/2.jpg' AS pi_path, '轻氧通勤衬衫·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-CM-02' AS pi_sku
UNION ALL
SELECT 7 AS pi_id, 'images/products/FL-CM-01/3.jpg' AS pi_path, '轻氧通勤衬衫·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-CM-02' AS pi_sku
UNION ALL
SELECT 8 AS pi_id, 'images/products/FL-CM-01/4.jpg' AS pi_path, '轻氧通勤衬衫·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-CM-02' AS pi_sku
UNION ALL
SELECT 9 AS pi_id, 'images/products/FL-CM-01/1.jpg' AS pi_path, '挺括九分烟管裤·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-CM-03' AS pi_sku
UNION ALL
SELECT 10 AS pi_id, 'images/products/FL-CM-01/1.jpg' AS pi_path, '双面呢通勤大衣·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-CM-04' AS pi_sku
UNION ALL
SELECT 11 AS pi_id, 'images/products/FL-CM-07/1.jpg' AS pi_path, '极简通勤托特包·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-CM-07' AS pi_sku
UNION ALL
SELECT 12 AS pi_id, 'images/products/FL-CM-07/2.jpg' AS pi_path, '极简通勤托特包·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-CM-07' AS pi_sku
UNION ALL
SELECT 13 AS pi_id, 'images/products/FL-CM-07/3.jpg' AS pi_path, '极简通勤托特包·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-CM-07' AS pi_sku
UNION ALL
SELECT 14 AS pi_id, 'images/products/FL-CM-07/4.jpg' AS pi_path, '极简通勤托特包·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-CM-07' AS pi_sku
UNION ALL
SELECT 15 AS pi_id, 'images/products/FL-CM-07/1.jpg' AS pi_path, '通勤真皮乐福鞋·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-CM-08' AS pi_sku
UNION ALL
SELECT 16 AS pi_id, 'images/products/FL-CM-07/2.jpg' AS pi_path, '通勤真皮乐福鞋·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-CM-08' AS pi_sku
UNION ALL
SELECT 17 AS pi_id, 'images/products/FL-CM-07/3.jpg' AS pi_path, '通勤真皮乐福鞋·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-CM-08' AS pi_sku
UNION ALL
SELECT 18 AS pi_id, 'images/products/FL-CM-07/4.jpg' AS pi_path, '通勤真皮乐福鞋·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-CM-08' AS pi_sku
UNION ALL
SELECT 19 AS pi_id, 'images/products/FL-CM-07/1.jpg' AS pi_path, '通勤哑光细腰带·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-CM-09' AS pi_sku
UNION ALL
SELECT 20 AS pi_id, 'images/products/FL-CM-07/1.jpg' AS pi_path, '通勤真丝丝巾·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-CM-10' AS pi_sku
UNION ALL
SELECT 21 AS pi_id, 'images/products/FL-LS-01/1.jpg' AS pi_path, '云朵卫衣·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-LS-01' AS pi_sku
UNION ALL
SELECT 22 AS pi_id, 'images/products/FL-LS-01/2.jpg' AS pi_path, '云朵卫衣·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-LS-01' AS pi_sku
UNION ALL
SELECT 23 AS pi_id, 'images/products/FL-LS-01/3.jpg' AS pi_path, '云朵卫衣·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-LS-01' AS pi_sku
UNION ALL
SELECT 24 AS pi_id, 'images/products/FL-LS-01/4.jpg' AS pi_path, '云朵卫衣·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-LS-01' AS pi_sku
UNION ALL
SELECT 25 AS pi_id, 'images/products/FL-LS-01/1.jpg' AS pi_path, '街头宽松T恤·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-LS-02' AS pi_sku
UNION ALL
SELECT 26 AS pi_id, 'images/products/FL-LS-01/2.jpg' AS pi_path, '街头宽松T恤·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-LS-02' AS pi_sku
UNION ALL
SELECT 27 AS pi_id, 'images/products/FL-LS-01/3.jpg' AS pi_path, '街头宽松T恤·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-LS-02' AS pi_sku
UNION ALL
SELECT 28 AS pi_id, 'images/products/FL-LS-01/4.jpg' AS pi_path, '街头宽松T恤·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-LS-02' AS pi_sku
UNION ALL
SELECT 29 AS pi_id, 'images/products/FL-LS-01/1.jpg' AS pi_path, '复古水洗牛仔外套·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-LS-03' AS pi_sku
UNION ALL
SELECT 30 AS pi_id, 'images/products/FL-LS-01/1.jpg' AS pi_path, '弹力运动短裤·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-LS-04' AS pi_sku
UNION ALL
SELECT 31 AS pi_id, 'images/products/FL-LS-07/1.jpg' AS pi_path, '街拍帆布斜挎包·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-LS-07' AS pi_sku
UNION ALL
SELECT 32 AS pi_id, 'images/products/FL-LS-07/2.jpg' AS pi_path, '街拍帆布斜挎包·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-LS-07' AS pi_sku
UNION ALL
SELECT 33 AS pi_id, 'images/products/FL-LS-07/3.jpg' AS pi_path, '街拍帆布斜挎包·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-LS-07' AS pi_sku
UNION ALL
SELECT 34 AS pi_id, 'images/products/FL-LS-07/4.jpg' AS pi_path, '街拍帆布斜挎包·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-LS-07' AS pi_sku
UNION ALL
SELECT 35 AS pi_id, 'images/products/FL-LS-07/1.jpg' AS pi_path, '休闲百搭板鞋·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-LS-08' AS pi_sku
UNION ALL
SELECT 36 AS pi_id, 'images/products/FL-LS-07/2.jpg' AS pi_path, '休闲百搭板鞋·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-LS-08' AS pi_sku
UNION ALL
SELECT 37 AS pi_id, 'images/products/FL-LS-07/3.jpg' AS pi_path, '休闲百搭板鞋·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-LS-08' AS pi_sku
UNION ALL
SELECT 38 AS pi_id, 'images/products/FL-LS-07/4.jpg' AS pi_path, '休闲百搭板鞋·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-LS-08' AS pi_sku
UNION ALL
SELECT 39 AS pi_id, 'images/products/FL-LS-07/1.jpg' AS pi_path, '撞色棒球帽·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-LS-09' AS pi_sku
UNION ALL
SELECT 40 AS pi_id, 'images/products/FL-LS-07/1.jpg' AS pi_path, '城市轻便腰包·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-LS-10' AS pi_sku
UNION ALL
SELECT 41 AS pi_id, 'images/products/FL-OD-01/1.jpg' AS pi_path, '三层防风冲锋衣·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OD-01' AS pi_sku
UNION ALL
SELECT 42 AS pi_id, 'images/products/FL-OD-01/2.jpg' AS pi_path, '三层防风冲锋衣·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OD-01' AS pi_sku
UNION ALL
SELECT 43 AS pi_id, 'images/products/FL-OD-01/3.jpg' AS pi_path, '三层防风冲锋衣·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OD-01' AS pi_sku
UNION ALL
SELECT 44 AS pi_id, 'images/products/FL-OD-01/4.jpg' AS pi_path, '三层防风冲锋衣·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OD-01' AS pi_sku
UNION ALL
SELECT 45 AS pi_id, 'images/products/FL-OD-01/1.jpg' AS pi_path, '轻量防晒皮肤衣·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OD-02' AS pi_sku
UNION ALL
SELECT 46 AS pi_id, 'images/products/FL-OD-01/2.jpg' AS pi_path, '轻量防晒皮肤衣·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OD-02' AS pi_sku
UNION ALL
SELECT 47 AS pi_id, 'images/products/FL-OD-01/3.jpg' AS pi_path, '轻量防晒皮肤衣·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OD-02' AS pi_sku
UNION ALL
SELECT 48 AS pi_id, 'images/products/FL-OD-01/4.jpg' AS pi_path, '轻量防晒皮肤衣·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OD-02' AS pi_sku
UNION ALL
SELECT 49 AS pi_id, 'images/products/FL-OD-01/1.jpg' AS pi_path, '抓绒保暖中层衣·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OD-03' AS pi_sku
UNION ALL
SELECT 50 AS pi_id, 'images/products/FL-OD-01/1.jpg' AS pi_path, '速干徒步长裤·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OD-04' AS pi_sku
UNION ALL
SELECT 51 AS pi_id, 'images/products/FL-OD-07/1.jpg' AS pi_path, '户外登山双肩包·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OD-07' AS pi_sku
UNION ALL
SELECT 52 AS pi_id, 'images/products/FL-OD-07/2.jpg' AS pi_path, '户外登山双肩包·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OD-07' AS pi_sku
UNION ALL
SELECT 53 AS pi_id, 'images/products/FL-OD-07/3.jpg' AS pi_path, '户外登山双肩包·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OD-07' AS pi_sku
UNION ALL
SELECT 54 AS pi_id, 'images/products/FL-OD-07/4.jpg' AS pi_path, '户外登山双肩包·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OD-07' AS pi_sku
UNION ALL
SELECT 55 AS pi_id, 'images/products/FL-OD-07/1.jpg' AS pi_path, '防滑溯溪鞋·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OD-08' AS pi_sku
UNION ALL
SELECT 56 AS pi_id, 'images/products/FL-OD-07/2.jpg' AS pi_path, '防滑溯溪鞋·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OD-08' AS pi_sku
UNION ALL
SELECT 57 AS pi_id, 'images/products/FL-OD-07/3.jpg' AS pi_path, '防滑溯溪鞋·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OD-08' AS pi_sku
UNION ALL
SELECT 58 AS pi_id, 'images/products/FL-OD-07/4.jpg' AS pi_path, '防滑溯溪鞋·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OD-08' AS pi_sku
UNION ALL
SELECT 59 AS pi_id, 'images/products/FL-OD-07/1.jpg' AS pi_path, '户外遮阳帽·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OD-09' AS pi_sku
UNION ALL
SELECT 60 AS pi_id, 'images/products/FL-OD-07/1.jpg' AS pi_path, '保温运动水壶·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OD-10' AS pi_sku
UNION ALL
SELECT 61 AS pi_id, 'images/products/FL-OC-01/1.jpg' AS pi_path, '香槟缎面晚礼服·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OC-01' AS pi_sku
UNION ALL
SELECT 62 AS pi_id, 'images/products/FL-OC-01/2.jpg' AS pi_path, '香槟缎面晚礼服·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OC-01' AS pi_sku
UNION ALL
SELECT 63 AS pi_id, 'images/products/FL-OC-01/3.jpg' AS pi_path, '香槟缎面晚礼服·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OC-01' AS pi_sku
UNION ALL
SELECT 64 AS pi_id, 'images/products/FL-OC-01/4.jpg' AS pi_path, '香槟缎面晚礼服·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OC-01' AS pi_sku
UNION ALL
SELECT 65 AS pi_id, 'images/products/FL-OC-01/1.jpg' AS pi_path, '赫本风小黑裙·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OC-02' AS pi_sku
UNION ALL
SELECT 66 AS pi_id, 'images/products/FL-OC-01/2.jpg' AS pi_path, '赫本风小黑裙·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OC-02' AS pi_sku
UNION ALL
SELECT 67 AS pi_id, 'images/products/FL-OC-01/3.jpg' AS pi_path, '赫本风小黑裙·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OC-02' AS pi_sku
UNION ALL
SELECT 68 AS pi_id, 'images/products/FL-OC-01/4.jpg' AS pi_path, '赫本风小黑裙·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OC-02' AS pi_sku
UNION ALL
SELECT 69 AS pi_id, 'images/products/FL-OC-01/1.jpg' AS pi_path, '法式碎花连衣裙·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OC-03' AS pi_sku
UNION ALL
SELECT 70 AS pi_id, 'images/products/FL-OC-01/1.jpg' AS pi_path, '丝绒西装礼服套装·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OC-04' AS pi_sku
UNION ALL
SELECT 71 AS pi_id, 'images/products/FL-OC-07/1.jpg' AS pi_path, '珍珠缀饰手拿包·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OC-07' AS pi_sku
UNION ALL
SELECT 72 AS pi_id, 'images/products/FL-OC-07/2.jpg' AS pi_path, '珍珠缀饰手拿包·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OC-07' AS pi_sku
UNION ALL
SELECT 73 AS pi_id, 'images/products/FL-OC-07/3.jpg' AS pi_path, '珍珠缀饰手拿包·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OC-07' AS pi_sku
UNION ALL
SELECT 74 AS pi_id, 'images/products/FL-OC-07/4.jpg' AS pi_path, '珍珠缀饰手拿包·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OC-07' AS pi_sku
UNION ALL
SELECT 75 AS pi_id, 'images/products/FL-OC-07/1.jpg' AS pi_path, '绸缎绅士领结·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-OC-08' AS pi_sku
UNION ALL
SELECT 76 AS pi_id, 'images/products/FL-OC-07/2.jpg' AS pi_path, '绸缎绅士领结·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-OC-08' AS pi_sku
UNION ALL
SELECT 77 AS pi_id, 'images/products/FL-OC-07/3.jpg' AS pi_path, '绸缎绅士领结·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-OC-08' AS pi_sku
UNION ALL
SELECT 78 AS pi_id, 'images/products/FL-OC-07/4.jpg' AS pi_path, '绸缎绅士领结·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-OC-08' AS pi_sku
UNION ALL
SELECT 79 AS pi_id, 'images/products/FL-OC-07/1.jpg' AS pi_path, '水晶晚宴耳饰·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OC-09' AS pi_sku
UNION ALL
SELECT 80 AS pi_id, 'images/products/FL-OC-07/1.jpg' AS pi_path, '礼服漆皮皮鞋·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-OC-10' AS pi_sku
UNION ALL
SELECT 81 AS pi_id, 'images/products/FL-HM-01/1.jpg' AS pi_path, '素色棉质睡衣套装·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-HM-01' AS pi_sku
UNION ALL
SELECT 82 AS pi_id, 'images/products/FL-HM-01/2.jpg' AS pi_path, '素色棉质睡衣套装·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-HM-01' AS pi_sku
UNION ALL
SELECT 83 AS pi_id, 'images/products/FL-HM-01/3.jpg' AS pi_path, '素色棉质睡衣套装·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-HM-01' AS pi_sku
UNION ALL
SELECT 84 AS pi_id, 'images/products/FL-HM-01/4.jpg' AS pi_path, '素色棉质睡衣套装·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-HM-01' AS pi_sku
UNION ALL
SELECT 85 AS pi_id, 'images/products/FL-HM-01/1.jpg' AS pi_path, '珊瑚绒家居袍·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-HM-02' AS pi_sku
UNION ALL
SELECT 86 AS pi_id, 'images/products/FL-HM-01/2.jpg' AS pi_path, '珊瑚绒家居袍·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-HM-02' AS pi_sku
UNION ALL
SELECT 87 AS pi_id, 'images/products/FL-HM-01/3.jpg' AS pi_path, '珊瑚绒家居袍·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-HM-02' AS pi_sku
UNION ALL
SELECT 88 AS pi_id, 'images/products/FL-HM-01/4.jpg' AS pi_path, '珊瑚绒家居袍·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-HM-02' AS pi_sku
UNION ALL
SELECT 89 AS pi_id, 'images/products/FL-HM-01/1.jpg' AS pi_path, '云柔家居短裤·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-HM-03' AS pi_sku
UNION ALL
SELECT 90 AS pi_id, 'images/products/FL-HM-01/1.jpg' AS pi_path, '软糯家居袜三双装·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-HM-04' AS pi_sku
UNION ALL
SELECT 91 AS pi_id, 'images/products/FL-HM-07/1.jpg' AS pi_path, '香薰蜡烛助眠套装·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-HM-07' AS pi_sku
UNION ALL
SELECT 92 AS pi_id, 'images/products/FL-HM-07/2.jpg' AS pi_path, '香薰蜡烛助眠套装·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-HM-07' AS pi_sku
UNION ALL
SELECT 93 AS pi_id, 'images/products/FL-HM-07/3.jpg' AS pi_path, '香薰蜡烛助眠套装·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-HM-07' AS pi_sku
UNION ALL
SELECT 94 AS pi_id, 'images/products/FL-HM-07/4.jpg' AS pi_path, '香薰蜡烛助眠套装·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-HM-07' AS pi_sku
UNION ALL
SELECT 95 AS pi_id, 'images/products/FL-HM-07/1.jpg' AS pi_path, '云朵记忆棉拖鞋·平铺正面' AS pi_alt, 10 AS pi_sort, 'FL-HM-08' AS pi_sku
UNION ALL
SELECT 96 AS pi_id, 'images/products/FL-HM-07/2.jpg' AS pi_path, '云朵记忆棉拖鞋·上身展示' AS pi_alt, 20 AS pi_sort, 'FL-HM-08' AS pi_sku
UNION ALL
SELECT 97 AS pi_id, 'images/products/FL-HM-07/3.jpg' AS pi_path, '云朵记忆棉拖鞋·面料细节' AS pi_alt, 30 AS pi_sort, 'FL-HM-08' AS pi_sku
UNION ALL
SELECT 98 AS pi_id, 'images/products/FL-HM-07/4.jpg' AS pi_path, '云朵记忆棉拖鞋·工艺细节' AS pi_alt, 40 AS pi_sort, 'FL-HM-08' AS pi_sku
UNION ALL
SELECT 99 AS pi_id, 'images/products/FL-HM-07/1.jpg' AS pi_path, '棉麻午睡眼罩·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-HM-09' AS pi_sku
UNION ALL
SELECT 100 AS pi_id, 'images/products/FL-HM-07/1.jpg' AS pi_path, '家居羊毛盖毯·款式示意' AS pi_alt, 10 AS pi_sort, 'FL-HM-10' AS pi_sku
) AS seed
JOIN product p ON p.sku = seed.pi_sku;

-- 交易数据经临时表按用户名/SKU解析外键，不写死自增ID。
DROP TEMPORARY TABLE IF EXISTS _fl_demo_guard, _fl_demo_orders,
    _fl_demo_items, _fl_demo_cart, _fl_demo_stock;
CREATE TEMPORARY TABLE _fl_demo_guard (
    check_name VARCHAR(100) NOT NULL,
    ok TINYINT NOT NULL,
    CONSTRAINT ck_fl_demo_guard CHECK (ok = 1)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _fl_demo_orders (
    order_no VARCHAR(40) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL,
    age_hours INT NOT NULL,
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL,
    user_id BIGINT NULL,
    is_new TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    shipped_at DATETIME NULL,
    completed_at DATETIME NULL
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _fl_demo_items (
    order_no VARCHAR(40) NOT NULL,
    sku VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    product_id BIGINT NULL,
    PRIMARY KEY (order_no, sku)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _fl_demo_cart (
    username VARCHAR(50) NOT NULL,
    sku VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    user_id BIGINT NULL,
    product_id BIGINT NULL,
    PRIMARY KEY (username, sku)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _fl_demo_stock (
    product_id BIGINT PRIMARY KEY,
    quantity INT NOT NULL
) ENGINE=InnoDB;

-- 空库初始化专用；不在应用正常处理交易时并行执行。先锁用户再锁商品。
SELECT id AS locked_user_id FROM store_user WHERE username IN ('alice', 'bob') ORDER BY id FOR UPDATE;
SELECT id AS locked_product_id FROM product ORDER BY id FOR UPDATE;
SET @fl_seed_now = CURRENT_TIMESTAMP;

INSERT INTO _fl_demo_orders (order_no, username, status, age_hours, receiver_name, receiver_phone, receiver_address) VALUES
    ('DEMO-202611-AL-01', 'alice', 'PENDING_PAYMENT', 1, '林晓', '13800000001', '教学示例地址：杭州市西湖区 FabricLab 实验工坊A（非真实收货地址）'),
    ('DEMO-202611-AL-02', 'alice', 'PAID', 25, '林晓', '13800000001', '教学示例地址：杭州市西湖区 FabricLab 实验工坊A（非真实收货地址）'),
    ('DEMO-202611-AL-03', 'alice', 'SHIPPED', 72, '林晓', '13800000001', '教学示例地址：杭州市西湖区 FabricLab 实验工坊A（非真实收货地址）'),
    ('DEMO-202611-AL-04', 'alice', 'COMPLETED', 168, '林晓', '13800000001', '教学示例地址：杭州市西湖区 FabricLab 实验工坊A（非真实收货地址）'),
    ('DEMO-202611-AL-05', 'alice', 'CANCELLED', 48, '林晓', '13800000001', '教学示例地址：杭州市西湖区 FabricLab 实验工坊A（非真实收货地址）'),
    ('DEMO-202611-BO-01', 'bob', 'PENDING_PAYMENT', 2, '陈晨', '13800000002', '教学示例地址：杭州市西湖区 FabricLab 实验工坊B（非真实收货地址）'),
    ('DEMO-202611-BO-02', 'bob', 'PAID', 49, '陈晨', '13800000002', '教学示例地址：杭州市西湖区 FabricLab 实验工坊B（非真实收货地址）'),
    ('DEMO-202611-BO-03', 'bob', 'SHIPPED', 96, '陈晨', '13800000002', '教学示例地址：杭州市西湖区 FabricLab 实验工坊B（非真实收货地址）'),
    ('DEMO-202611-BO-04', 'bob', 'COMPLETED', 192, '陈晨', '13800000002', '教学示例地址：杭州市西湖区 FabricLab 实验工坊B（非真实收货地址）'),
    ('DEMO-202611-BO-05', 'bob', 'CANCELLED', 24, '陈晨', '13800000002', '教学示例地址：杭州市西湖区 FabricLab 实验工坊B（非真实收货地址）');
INSERT INTO _fl_demo_items (order_no, sku, quantity, unit_price) VALUES
    ('DEMO-202611-AL-01', 'FL-LS-01', 1, 189.00),
    ('DEMO-202611-AL-01', 'FL-CM-03', 1, 259.00),
    ('DEMO-202611-AL-02', 'FL-HM-01', 1, 199.00),
    ('DEMO-202611-AL-02', 'FL-LS-02', 1, 89.00),
    ('DEMO-202611-AL-03', 'FL-OD-01', 1, 899.00),
    ('DEMO-202611-AL-03', 'FL-OD-07', 1, 459.00),
    ('DEMO-202611-AL-04', 'FL-HM-09', 2, 35.00),
    ('DEMO-202611-AL-04', 'FL-HM-03', 1, 79.00),
    ('DEMO-202611-AL-05', 'FL-CM-06', 1, 99.00),
    ('DEMO-202611-AL-05', 'FL-CM-01', 2, 429.00),
    ('DEMO-202611-BO-01', 'FL-OD-02', 1, 299.00),
    ('DEMO-202611-BO-01', 'FL-LS-12', 2, 29.00),
    ('DEMO-202611-BO-02', 'FL-LS-08', 1, 259.00),
    ('DEMO-202611-BO-02', 'FL-OC-12', 1, 119.00),
    ('DEMO-202611-BO-03', 'FL-HM-04', 3, 39.00),
    ('DEMO-202611-BO-03', 'FL-OC-09', 1, 149.00),
    ('DEMO-202611-BO-04', 'FL-OC-03', 1, 559.00),
    ('DEMO-202611-BO-04', 'FL-OD-12', 1, 39.00),
    ('DEMO-202611-BO-05', 'FL-OC-01', 1, 1299.00),
    ('DEMO-202611-BO-05', 'FL-OC-11', 1, 259.00);
INSERT INTO _fl_demo_cart (username, sku, quantity) VALUES
    ('alice', 'FL-CM-01', 1),
    ('alice', 'FL-LS-02', 2),
    ('alice', 'FL-CM-07', 1),
    ('alice', 'FL-LS-03', 1),
    ('alice', 'FL-CM-06', 1),
    ('alice', 'FL-OD-03', 1),
    ('bob', 'FL-OD-01', 1),
    ('bob', 'FL-HM-01', 2),
    ('bob', 'FL-OC-11', 1),
    ('bob', 'FL-CM-12', 1);

-- 外键通过用户名及SKU查找，不写死数据库自增ID。
UPDATE _fl_demo_orders d LEFT JOIN store_user u ON u.username = d.username
SET d.user_id = u.id;
UPDATE _fl_demo_items i LEFT JOIN product p ON p.sku = i.sku
SET i.product_id = p.id;
UPDATE _fl_demo_cart c LEFT JOIN store_user u ON u.username = c.username
LEFT JOIN product p ON p.sku = c.sku SET c.user_id = u.id, c.product_id = p.id;
INSERT INTO _fl_demo_guard
SELECT 'required customers and products', IF(
    NOT EXISTS (SELECT 1 FROM _fl_demo_orders d LEFT JOIN store_user u ON u.id = d.user_id
                WHERE u.id IS NULL OR u.role <> 'CUSTOMER' OR u.status <> 'ENABLED')
    AND NOT EXISTS (SELECT 1 FROM _fl_demo_items WHERE product_id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM _fl_demo_cart WHERE user_id IS NULL OR product_id IS NULL), 1, 0);
UPDATE _fl_demo_orders d LEFT JOIN sales_order o ON o.order_no = d.order_no
SET d.is_new = IF(o.id IS NULL, 1, 0);

-- 只对本次新建且未取消的订单净扣库存；取消样例净影响为0。
INSERT INTO _fl_demo_stock (product_id, quantity)
SELECT i.product_id, SUM(i.quantity) FROM _fl_demo_items i
JOIN _fl_demo_orders d ON d.order_no = i.order_no
WHERE d.is_new = 1 AND d.status <> 'CANCELLED' GROUP BY i.product_id;
INSERT INTO _fl_demo_guard
SELECT 'sufficient stock for new active orders', IF(
    NOT EXISTS (SELECT 1 FROM _fl_demo_stock s JOIN product p ON p.id = s.product_id
                WHERE p.stock < s.quantity), 1, 0);

-- 时间以本次导入为基准生成过去1小时至8天的教学订单，时间先后关系由CHECK约束把关。
UPDATE _fl_demo_orders SET created_at = TIMESTAMPADD(HOUR, -age_hours, @fl_seed_now);
UPDATE _fl_demo_orders SET
    paid_at = CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN TIMESTAMPADD(MINUTE, 5, created_at) END,
    cancelled_at = CASE WHEN status = 'CANCELLED' THEN TIMESTAMPADD(MINUTE, 15, created_at) END,
    shipped_at = CASE WHEN status IN ('SHIPPED','COMPLETED') THEN TIMESTAMPADD(HOUR, 24, created_at) END,
    completed_at = CASE WHEN status = 'COMPLETED' THEN TIMESTAMPADD(HOUR, 72, created_at) END;

INSERT INTO sales_order
    (order_no, user_id, status, total_amount, receiver_name, receiver_phone, receiver_address,
     created_at, updated_at, payment_method, payment_no, paid_at, cancelled_at, shipped_at, completed_at)
SELECT d.order_no, d.user_id, d.status, totals.amount, d.receiver_name, d.receiver_phone, d.receiver_address,
       d.created_at, COALESCE(d.completed_at, d.shipped_at, d.cancelled_at, d.paid_at, d.created_at),
       CASE WHEN d.paid_at IS NOT NULL THEN 'MOCK' END,
       CASE WHEN d.paid_at IS NOT NULL THEN CONCAT('MOCK-', d.order_no) END,
       d.paid_at, d.cancelled_at, d.shipped_at, d.completed_at
FROM _fl_demo_orders d
JOIN (SELECT order_no, SUM(unit_price * quantity) AS amount FROM _fl_demo_items GROUP BY order_no) totals
     ON totals.order_no = d.order_no
WHERE d.is_new = 1 ORDER BY d.order_no;

-- 成交单价直接存快照；AL-04/BO-04 两张完成订单展示历史价与现价的差异。
INSERT INTO sales_order_item
    (order_id, product_id, sku_snapshot, product_name_snapshot, cover_image_snapshot, unit_price, quantity, line_amount)
SELECT o.id, p.id, p.sku, p.name, p.cover_image, i.unit_price, i.quantity, i.unit_price * i.quantity
FROM _fl_demo_items i
JOIN _fl_demo_orders d ON d.order_no = i.order_no
JOIN sales_order o ON o.order_no = d.order_no
JOIN product p ON p.id = i.product_id
WHERE d.is_new = 1 ORDER BY o.id, p.id;
UPDATE product p JOIN _fl_demo_stock s ON s.product_id = p.id
SET p.stock = p.stock - s.quantity, p.version = p.version + 1;

-- 加车不扣库存；含售罄、下架失效条目与低库存条目，供购物车校验演示。
INSERT INTO cart_item (user_id, product_id, quantity, version)
SELECT c.user_id, c.product_id, c.quantity, 0 FROM _fl_demo_cart c
WHERE NOT EXISTS (SELECT 1 FROM cart_item x WHERE x.user_id = c.user_id AND x.product_id = c.product_id)
ORDER BY c.user_id, c.product_id;

DROP TEMPORARY TABLE _fl_demo_guard, _fl_demo_orders,
    _fl_demo_items, _fl_demo_cart, _fl_demo_stock;
COMMIT;

-- 计数核对：预期4、15、60、100、10、10、20（54个上架，6个下架，3个上架售罄）。
SELECT 'store_user' AS table_name, COUNT(*) AS row_count FROM store_user
UNION ALL SELECT 'category', COUNT(*) FROM category
UNION ALL SELECT 'product', COUNT(*) FROM product
UNION ALL SELECT 'product_image', COUNT(*) FROM product_image
UNION ALL SELECT 'cart_item', COUNT(*) FROM cart_item
UNION ALL SELECT 'sales_order', COUNT(*) FROM sales_order
UNION ALL SELECT 'sales_order_item', COUNT(*) FROM sales_order_item;

-- 状态分布核对：预期ON_SALE=54、DRAFT=6；上架售罄=3；库存1=3。
SELECT 'status' AS check_name, status AS detail, COUNT(*) AS row_count FROM product GROUP BY status
UNION ALL SELECT 'on-sale sold-out', NULL, COUNT(*) FROM product WHERE status = 'ON_SALE' AND stock = 0
UNION ALL SELECT 'on-sale stock=1', NULL, COUNT(*) FROM product WHERE status = 'ON_SALE' AND stock = 1;