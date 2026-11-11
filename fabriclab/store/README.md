# fabriclab-store

FabricLab-Atelier 用户端工程，端口 8080。当前阶段：T2 数据层与服装模型。

## 结构

```
src/main/java/com/fabriclab/store/
├── StoreApplication.java            启动类
├── controller/MainController.java   首页与健康检查
├── service/BrandService.java        品牌展示信息
├── entity/                          七张业务表实体
│   StoreUser / Category / Product / ProductImage
│   CartItem / SalesOrder / SalesOrderItem
└── mapper/                          对应 Mapper，继承 BaseMapper
src/main/resources/application.yml   端口、数据源、MyBatis-Plus
src/test/java/com/fabriclab/store/
├── controller/MainControllerTest.java
├── service/BrandServiceTest.java
├── entity/EntityMappingTest.java     表名、主键、生成列、单 SKU 口径
└── SchemaIntegrationTest.java        真实库连通与七表存在性（需 MYSQL_PASSWORD）
```

## 数据层约定

- DDL 唯一来源是仓库根目录 `01-schema.sql`，只在空库执行一次；应用启动不建表、不清库。
- 连接参数走环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`，口令不入仓库。
- `category.parent_key` 是数据库生成列，实体不写入。
- 单 SKU 口径：款号 `sku` 创建后不可变；不设尺码、配色字段，差异写入商品名称与描述。
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
| `http://localhost:8080/health` | `{"status":"UP","app":"fabriclab-store","stage":"T2"}` |

环境：JDK 25、Maven 3.9.16、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、MySQL 8.4。
