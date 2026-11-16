# 数据库与种子数据

用户端（store，端口 8080）使用 MySQL 库 `MyPetStore`，七张业务表与管理端共享。

## 导入顺序

空库按顺序整文件执行三个脚本：

1. 建表 DDL：仓库根目录 `01-schema.sql`（课程规定的七张表）
2. 种子数据：本目录 `seed-data.sql`（仅 INSERT，不含 DDL）
3. 客服扩展：本目录 `customer-service.sql`（两张咨询扩展表 + 演示数据，可重复执行）

```bash
mysql -u root -p < 01-schema.sql
mysql -u root -p < fabriclab/store/src/main/resources/db/seed-data.sql
mysql -u root -p < fabriclab/store/src/main/resources/db/customer-service.sql
```

第 3 步是可选扩展：不导入时客服页面不可用，但控制台、铃铛与其余七张表的功能不受影响。种子脚本面向空库一次性导入；已有同名账号/SKU/订单号时会在主键或唯一键处报错停止，请勿对已导入库重复执行。客服扩展脚本按编号与消息内容判重，可安全重复执行。

## 测试库隔离（必须）

集成测试会清空并重建订单、购物车等 fixture，**禁止对 `MyPetStore` 演示库执行 `mvn test`**。两个工程的 `maven-surefire-plugin` 已把测试的 `MYSQL_DATABASE` 固定为 `mypetstoretest`，需要改库名用 `-Dfl.test.database=<库名>`。首次执行前在 3307 临时实例建库（root 无口令）：

```bash
M="mysql -h127.0.0.1 -P3307 -uroot"
MYSQL_PWD= $M -e "DROP DATABASE IF EXISTS mypetstoretest; CREATE DATABASE mypetstoretest CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
for f in 01-schema.sql fabriclab/store/src/main/resources/db/seed-data.sql fabriclab/store/src/main/resources/db/customer-service.sql; do
  sed 's/CREATE DATABASE IF NOT EXISTS MyPetStore$/CREATE DATABASE IF NOT EXISTS mypetstoretest/; s/USE MyPetStore;/USE mypetstoretest;/' "$f" | MYSQL_PWD= $M
done
MYSQL_PWD= $M mypetstoretest -e "DELETE FROM service_message; DELETE FROM service_thread;"
```

测试库不带客服演示会话，客服列表/搜索断言才不受演示数据干扰；演示库始终保持上表的完整数据。

## 演示账号

统一口令 `Passw0rd!2026`（BCrypt cost=10，仅本地教学用，不作真实部署凭据）。

| 用户名 | 显示名 | 角色 | 状态 | 用途 |
|---|---|---|---|---|
| alice | 林晓 | CUSTOMER | ENABLED | 购物车 6 项、订单 5 张（DEMO-202611-AL-01~05） |
| bob | 陈晨 | CUSTOMER | ENABLED | 购物车 4 项、订单 5 张（DEMO-202611-BO-01~05） |
| disabled | 禁用教学账号 | CUSTOMER | DISABLED | 登录被拒场景 |
| admin | 平台管理员 | ADMIN | ENABLED | 管理端登录 |

## 种子数据规模

| 表 | 行数 | 说明 |
|---|---|---|
| store_user | 4 | 见上演示账号 |
| category | 15 | 一级 5 + 二级 10 |
| product | 60 | SKU = FL-<CM/LS/OD/OC/HM>-01..12 |
| product_image | 79 | 初始 100 张，「一致性修正」删去 21 张，部分款号相册不满 4 图 |
| cart_item | 11 | alice 6 项 / bob 5 项 |
| sales_order | 10 | AL-01~05 / BO-01~05 |
| sales_order_item | 20 | 每单 2 条 |
| service_thread | 4 | 客服扩展：待回复 2 / 已回复 1 / 已关闭 1 |
| service_message | 9 | 客服扩展：含客服回复与会员追问 |

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

全部商品图取自公开网页的真实商品/面料摄影（经必应图片检索定位），**非 AI 生成**；检索与筛选过程记录于 PR 描述。图片仅用于课程作业演示，不作商用；FL-CM-07 因真实皮革特写仅寻得 2 张，其 3/4 位为同图局部裁切变体。`logo.png` 为品牌 VI 定稿标志「交织裁片」的程序渲染图（自有设计，无第三方版权）。

| 款号 | 商品 | 图片来源（位1 / 位2 / 位3 / 位4） |
|---|---|---|
| FL-CM-01 | 云感直筒西装外套 | [NOWRE 优衣库U系列](https://nowre.com/fashion/711586/uniqlo-u-2022-qiudongxilie-9-yue-23-rizhengshishishangshi/) / [优衣库中国 23SS lookbook](https://www.uniqlo.cn/htmlcode_U_23SLK.html?index=1) / [1688 面料商行](https://detail.1688.com/offer/536109821269.html) / [知乎专栏·羊绒](https://zhuanlan.zhihu.com/p/24391569) |
| FL-CM-07 | 极简通勤托特包 | [昵图网·皮革纹理](https://www.nipic.com/show/43532007.html) / [摄图网·黑皮素材](https://www.shetu66.com/so-sucai/heisebeijing/) / 同位1裁切 / 同图2裁切 |
| FL-LS-01 | 云朵卫衣 | [中国纺联面料图](https://www.alltextile.cn/new/new-9-418.html) / [说明书百科·毛圈布](https://www.shuomingshu.cn/baike/162007.html) / [知乎专栏·织物](https://zhuanlan.zhihu.com/p/52757598) / [知乎专栏·织纹](https://zhuanlan.zhihu.com/p/634187050) |
| FL-LS-07 | 街拍帆布斜挎包 | [1688 帆布袋厂①](https://detail.1688.com/offer/546416363002.html) / [1688 帆布袋厂②](https://detail.1688.com/offer/572874432198.html) / [1688 帆布袋厂③](https://detail.1688.com/offer/521035570221.html) / [51lip 帆布包](http://www.51lip.com/product-68433.html) |
| FL-OD-01 | 三层防风冲锋衣 | [素材站·冲锋衣白底](https://www.mksucai.com/png/ygljjaod.html) / [NOWRE·始祖鸟](https://nowre.com/editorial/801067/) / [什么值得买·夹克](https://post.smzdm.com/p/ad9zrqxd/) / [PNG素材·软壳](https://www.pngsucai.com/png/8911593.html) |
| FL-OD-07 | 户外登山双肩包 | [什么值得买·登山包](https://post.smzdm.com/talk/p/a60on9eg/) / [ThePost·徒步](https://thepost.ph/travel/worlds-best-destinations-for-hiking/) / [知乎专栏·登山包](https://zhuanlan.zhihu.com/p/136630948) / [HikeMatic·徒步](https://hikematic.com/boulder-co-hiking-trails/) |
| FL-OC-01 | 香槟缎面晚礼服 | [全球纺织网·醋酸缎](https://www.tnc.com.cn/products/detail-13596779.html) / [知乎专栏·绸缎①](https://zhuanlan.zhihu.com/p/59568944) / 同上② / [好5128·缎面](https://www.hao528.com/ss/202203/2320917.html) |
| FL-OC-07 | 珍珠缀饰手拿包 | [百度百科·Pearl](https://baike.baidu.com/item/Pearl/23812671) ①②③④ |
| FL-HM-01 | 素色棉质睡衣套装 | [51教习网·棉布](https://yyld.51jiaoxi.com/bixiusan/wyb_danci.html) / [Sanyu·棉织流程](https://sanyubabies.com/what-is-cotton-made-of-how-cotton-is-made-a-comprehensive-guide-from-seed-to-shelf/) / [知乎专栏·织物](https://zhuanlan.zhihu.com/p/52757598) / [知乎专栏·斜纹](https://zhuanlan.zhihu.com/p/634187050) |
| FL-HM-07 | 香薰蜡烛助眠套装 | [什么值得买·香薰评测](https://post.smzdm.com/p/azo9204o/) ①②③ / [知乎问答·蜡烛](https://www.zhihu.com/question/296849891) |

### 面料档案：材质实拍

`store/src/main/resources/static/images/fibres/` 下的九张图用于 `/fabrics` 四类纤维与 `/crafts` 前两道工序，全部取自 [Pexels](https://www.pexels.com/license/) 的公开摄影（免费使用、无需署名，**非 AI 生成**），按纤维归类，不做合成或重绘。

| 文件 | 画面 | 来源页 |
|---|---|---|
| cotton-01.jpg | 原棉 · 未轧的棉铃 | https://www.pexels.com/photo/6168152/ |
| cotton-02.jpg | 本白棉布 · 未染布面 | https://www.pexels.com/photo/7232397/ |
| linen-01.jpg | 雨露麻 · 平纹织面 | https://www.pexels.com/photo/7794365/ |
| silk-01.jpg | 桑蚕丝 · 本色缎面 | https://www.pexels.com/photo/8465943/ |
| silk-02.jpg | 生丝 · 折叠后的光泽 | https://www.pexels.com/photo/14935628/ |
| wool-01.jpg | 未染羊毛 · 针织组织 | https://www.pexels.com/photo/6275937/ |
| wool-02.jpg | 粗纺羊毛 · 经纬与绒毛 | https://www.pexels.com/photo/32984627/ |
| other-01.jpg | 加厚帆布 · 辅料平纹 | https://www.pexels.com/photo/7533979/ |
| other-02.jpg | 帆布边缘 · 组织与密度 | https://www.pexels.com/photo/7533973/ |

工艺页第三、四道用 `images/look/lb-07.jpg`、`lb-03.jpg`（成衣上身），与本项目其他 `look/` 大片同批次采集，页面上标注为 ARCHIVE。

以上图片用于课程作业的教学演示，均已注明来源页；如版权方提出异议将整体替换。
