# fabriclab-store

FABRICLAB 用户端工程，端口 8080。当前阶段：T5 首页与商品列表。

## 结构

```
src/main/java/com/fabriclab/store/
├── StoreApplication.java            启动类
├── config/MybatisPlusConfig.java    分页插件（数据库 LIMIT 分页）
├── controller/MainController.java   首页（新品上架 ≤3、精选推荐 ≤4）与健康检查
├── controller/BrowseController.java 商品列表 /category：GET 整页刷新
├── controller/GlobalExceptionHandler.java 400 / 404 / 500 统一错误页
├── service/BrandService.java        品牌展示信息
├── service/CategoryService.java     两级分类：编码唯一、同级名唯一、二级必须挂一级
├── service/ProductService.java      单 SKU 商品：款号唯一、必归二级分类、version 递增
├── service/BrowseService.java       浏览：分类作用域、名称包含匹配、排序白名单、分页
├── model/CategoryType.java          类型三值由分类编码后缀映射（*_GAR / *_ACC / 其余 ALL）
├── model/BrowseQuery.java           列表参数解析：页码归一、页大小 1—50、关键字 ≤120
├── model/SortOption.java            排序四值白名单：DEFAULT / PRICE_ASC / PRICE_DESC / NEWEST
├── model/SingleSkuText.java         尺码配色并入名称、面料档案纯文本落点
├── exception/BusinessException.java 业务校验异常 → HTTP 400
├── exception/NotFoundException.java 资源不存在 → HTTP 404（分类不可用）
├── entity/                          七张业务表实体
│   StoreUser / Category / Product / ProductImage
│   CartItem / SalesOrder / SalesOrderItem
└── mapper/                          对应 Mapper，继承 BaseMapper
src/main/resources/application.yml   端口、数据源、MyBatis-Plus
src/main/resources/templates/        index 首页 / category 列表 / error 错误 / fragments 卡片
src/test/java/com/fabriclab/store/
├── controller/MainControllerTest.java   首页渲染（Mock 服务层）
├── controller/BrowseControllerTest.java 列表 400 / 404 / 条件回显
├── service/BrandServiceTest.java
├── service/CategoryServiceTest.java 两级约束、重复编码、更新不可变字段
├── service/ProductServiceTest.java  款号唯一、二级归属、version 递增
├── service/BrowseServiceTest.java   分类作用域、类型切换升级、通配符转义、两栏上限
├── model/BrowseQueryTest.java       页码归一、页大小白名单、关键字截断拒绝
├── model/CategoryTypeTest.java      编码后缀 → 类型三值映射
├── model/SingleSkuTextTest.java     变体名与面料档案
├── entity/EntityMappingTest.java     表名、主键、生成列、单 SKU 口径
├── BrowseIntegrationTest.java       真库首页两栏与列表验收 AC-01~03（需 MYSQL_PASSWORD）
└── SchemaIntegrationTest.java        真实库连通与七表存在性（需 MYSQL_PASSWORD）
```

## 数据层约定

- DDL 唯一来源是仓库根目录 `01-schema.sql`，只在空库执行一次；应用启动不建表、不清库。
- 连接参数走环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`，口令不入仓库。
- `category.parent_key` 是数据库生成列，实体不写入。
- 单 SKU 口径：款号 `sku` 创建后不可变；不设尺码、配色字段，差异经 `SingleSkuText` 写入商品名称与描述；面料 / 克重 / 版型 / 工艺为多行纯文本。
- 分类编码后缀即类型：`*_GAR` 成衣、`*_ACC` 配饰、其余 `ALL`；映射在 `CategoryType`，不新增表字段。
- 金额用 `BigDecimal`，库存与数量用整数，订单明细保存成交快照。

## 构建与验证

```bash
mvn test
mvn spring-boot:run --server.port=8080
```

宿主环境若注入 `SERVER__PORT`，会覆盖配置文件端口；显式指定 `--server.port=8080` 可保证落在 8080。

数据库连通性验证需先准备数据：

```bash
export MYSQL_PASSWORD='<本机口令>'
mvn test -Dtest=SchemaIntegrationTest
```

| 地址 | 结果 |
|---|---|
| `http://localhost:8080/main` | 品牌首页：系列线导航、新品上架 ≤3（created_at、id 倒序）、精选推荐 ≤4（sort_order、id 升序） |
| `http://localhost:8080/category` | 商品列表：两级分类筛选、类型按钮、名称搜索、排序四值、分页默认 6（1—50） |
| `http://localhost:8080/health` | `{"status":"UP","app":"fabriclab-store","stage":"T5"}` |

浏览口径：仅上架商品出现在动态区域，零库存仍展示为「暂时缺货」；搜索只匹配名称（去首尾空白、≤120，`%` `_` 按普通字符），不搜款号；非法参数 400，分类不存在 404「分类不可用」，技术异常 500 通用失败页。

环境：JDK 25、Maven 3.9.16、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、MySQL 8.4。
