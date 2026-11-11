# 数据库与种子数据

用户端（store，端口 8080）使用 MySQL 库 `MyPetStore`，七张业务表与管理端共享。

## 导入顺序

空库按顺序整文件执行两个脚本：

1. 建表 DDL：仓库根目录 `01-schema.sql`
2. 种子数据：本目录 `seed-data.sql`（仅 INSERT，不含 DDL）

```bash
mysql -u root -p < 01-schema.sql
mysql -u root -p < fabriclab/store/src/main/resources/db/seed-data.sql
```

种子脚本面向空库一次性导入；已有同名账号/SKU/订单号时会在主键或唯一键处报错停止，请勿对已导入库重复执行。

## 演示账号

统一口令 `Passw0rd!2026`（BCrypt cost=10，仅本地教学用，不作真实部署凭据）。

| 用户名 | 显示名 | 角色 | 状态 | 用途 |
|---|---|---|---|---|
| alice | 林晓 | CUSTOMER | ENABLED | 购物车 6 项、订单 5 张（DEMO-202611-AL-01~05） |
| bob | 陈晨 | CUSTOMER | ENABLED | 购物车 4 项、订单 5 张（DEMO-202611-BO-01~05） |
| disabled | 禁用教学账号 | CUSTOMER | DISABLED | 登录被拒场景 |
| admin | 工坊管理员 | ADMIN | ENABLED | 管理端登录 |

## 种子数据规模

| 表 | 行数 | 说明 |
|---|---|---|
| store_user | 4 | 见上演示账号 |
| category | 15 | 一级 5 + 二级 10 |
| product | 60 | SKU = FL-<CM/LS/OD/OC/HM>-01..12 |
| product_image | 100 | 10 款 4 图 + 20 款 1 图 |
| cart_item | 10 | alice 6 项 / bob 4 项 |
| sales_order | 10 | AL-01~05 / BO-01~05 |
| sales_order_item | 20 | 每单 2 条 |

### 场景覆盖

| 场景 | 数据 |
|---|---|
| 下架（DRAFT） | FL-CM-05 / FL-HM-02 / FL-HM-11 / FL-LS-10 / FL-OC-08 / FL-OD-03 |
| 上架售罄（stock=0） | FL-CM-06 / FL-OC-11 / FL-OD-05 |
| 库存 1（临界） | FL-HM-06 / FL-LS-03 / FL-OC-01 |
| 无相册回退封面 | 每二级分类后两款，共 20 款 |
| 订单五状态 | PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED 各 2 张 |
| MOCK 支付 | 6 张，payment_no = MOCK-<order_no> |
| 历史价快照差异 | DEMO-202611-AL-04（FL-HM-09）、DEMO-202611-BO-04（FL-OC-03） |
| 取消单不净扣库存 | FL-CM-01 库存保持 25 |

## 图片来源与许可

商品图位于 `store/src/main/resources/static/images/products/<款号>/1~4.jpg`，语义依次为平铺正面、上身展示、面料细节、工艺细节。10 个款号目录各 4 张，同二级分类内商品相册复用所属线主款目录，不重复占用图片文件。

| 款号目录 | 原型素材 |
|---|---|
| FL-CM-01 | K9-BGL-03 |
| FL-CM-07 | K9-RTV-11 |
| FL-LS-01 | FL-DLH-02 |
| FL-LS-07 | CT-SIA-05 |
| FL-OD-01 | AQ-KOI-04 |
| FL-OD-07 | AQ-CRF-12 |
| FL-OC-01 | BR-CNR-08 |
| FL-OC-07 | BRD-MAC-07 |
| FL-HM-01 | RP-SNK-02 |
| FL-HM-07 | RP-IGU-09 |

以上图片为课程原型素材的教学复用，仅用于课程演示，不得商用；目录名已映射为 FabricLab 款号，不表示同图商品的实际品相。
