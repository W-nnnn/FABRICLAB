# fabriclab-store

FabricLab-Atelier 用户端工程，端口 8080。当前阶段：T3 分类体系与商品模型。

## 结构

```
src/main/java/com/fabriclab/store/
├── StoreApplication.java            启动类
├── controller/MainController.java   首页与健康检查
├── service/BrandService.java        品牌展示信息
├── service/CategoryService.java     两级分类：编码唯一、同级名唯一、二级必须挂一级
├── service/ProductService.java      单 SKU 商品：款号唯一、必归二级分类、version 递增
├── model/CategoryType.java          类型三值由分类编码后缀映射（*_GAR / *_ACC / 其余 ALL）
├── model/SingleSkuText.java         尺码配色并入名称、面料档案纯文本落点
├── exception/BusinessException.java 业务校验异常
├── entity/                          七张业务表实体
│   StoreUser / Category / Product / ProductImage
│   CartItem / SalesOrder / SalesOrderItem
└── mapper/                          对应 Mapper，继承 BaseMapper
src/main/resources/application.yml   端口、数据源、MyBatis-Plus
src/test/java/com/fabriclab/store/
├── controller/MainControllerTest.java
├── service/BrandServiceTest.java
├── service/CategoryServiceTest.java 两级约束、重复编码、更新不可变字段
├── service/ProductServiceTest.java  款号唯一、二级归属、version 递增
├── model/CategoryTypeTest.java      编码后缀 → 类型三值映射
├── model/SingleSkuTextTest.java     变体名与面料档案
├── entity/EntityMappingTest.java     表名、主键、生成列、单 SKU 口径
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
| `http://localhost:8080/main` | 品牌占位模板 |
| `http://localhost:8080/health` | `{"status":"UP","app":"fabriclab-store","stage":"T3"}` |

环境：JDK 25、Maven 3.9.16、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、MySQL 8.4。
