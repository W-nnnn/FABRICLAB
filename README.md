<p><img src="fabriclab/store/src/main/resources/static/images/mark.png" alt="FABRICLAB 标志" width="72"></p>

# FABRICLAB · 未染

[English](README.en.md)

《软件开发架构平台》实验一的实现。项目由用户端和管理端两个独立的 Spring Boot 应用组成，共享一个 MySQL 数据库，覆盖商品选购、账户、购物车、订单、模拟支付、售前咨询和后台管理。

## 品牌

标志取自交织裁片，透明底。标准色：墨黑 `#1A1A1A`、米白 `#F2EFE9`、陶土 `#B4643A`。

商品图片来源与许可见 [数据库说明](fabriclab/store/src/main/resources/db/README.md)。

## 功能

**用户端 · 8080**

- 选购：`/main`、`/category`、`/series/{id}`，按系列和分类浏览，支持搜索、排序、分页。
- 商品：`/product?id=`、`/fabrics`、`/crafts`。商品档案记录款号、面料、克重、版型和缝制工艺；系列页、面料页、工艺页由同一批商品数据生成。
- 账户：注册、登录、服务端验证码、BCrypt、会话校验、站内返回地址白名单。
- 购物车与订单：购物车落库；结算按服务端快照核价；订单按状态管理；取消通过条件更新回补库存。
- 模拟支付：金额只取订单 `total_amount`，失败不写库，重复成功保留首次凭证。
- 客服：顶部导航独立入口，`/service` 单页；游客查看常见问题，会员发起和查看咨询。客服不与账户中心混排。

**管理端 · 8081**

- 商品：分类、款号、上下架、相册、封面和库存维护。
- 用户：顾客账号资料、口令重置、启停；角色或状态变化会使在线会话失效。
- 订单：按订单号查询、状态筛选、收货信息修改、条件发货、受限删除。
- 客服：`/admin/service` 工作台，咨询列表、详情、回复、关闭和重开。
- 语言：界面文案与商品内容均支持中英切换。

客服处理售前选购问题。控制台不显示未经真实统计的经营指标。

## 技术约定

- JDK 25，Maven 3.9.16，Spring Boot 3.5.16，MyBatis-Plus 3.5.18。
- MySQL 8.4（本机 3306 实例），演示库 `FABRICLAB`、测试库 `FABRICLAB_TEST`，7 张课程业务表 + 2 张客服扩展表。课程规定的表名、字段名和需求编号保留；页面、文案、示例数据和注释使用服装语义。
- Thymeleaf 服务端渲染，无前端构建步骤。
- `STORESESSION` / `ADMINSESSION` 仅通过 Cookie 传递；所有 POST 校验 CSRF；受保护入口逐次检查角色和启用状态。
- 金额使用 `BigDecimal`，订单明细保存成交快照。支付与取消通过条件更新互斥，补货通过 `stock + version` 避免覆盖。
- `CheckoutFaultPoint` / `CancelFaultPoint` 在生产代码中为空实现，只在验收场景中使用。

## 运行

依赖 JDK 25、Maven 3.9.16 和 MySQL 8.4。

数据库准备（建库 `FABRICLAB` / `FABRICLAB_TEST`、创建连接账号、按顺序导入建表与两个种子脚本）的一次性命令见 [数据库与种子数据](fabriclab/store/src/main/resources/db/README.md)。客服扩展脚本按编号和正文判重，可重复执行；不导入时仅客服页不可用。

连接参数已写入两端 `application.yml` 的默认值（库 `FABRICLAB`、账号 `fabriclab/fabriclab`），启动不需要环境变量：

```bash
cd fabriclab/store && mvn spring-boot:run
cd fabriclab/admin && mvn spring-boot:run
```

| 地址 | 用途 |
|---|---|
| `http://localhost:8080/main` | 用户端首页 |
| `http://localhost:8081/admin` | 管理端入口，未登录跳登录页 |

演示口令为 `Passw0rd!2026`，仅用于本地教学。`alice` / `bob` 为顾客，`disabled` 为禁用样例，`admin` 为管理员。

运行测试：

```bash
export MYSQL_USER=fabriclab
export MYSQL_PASSWORD=fabriclab
cd fabriclab/store && mvn test
cd fabriclab/admin && mvn test
```

两个工程的测试库固定为 `FABRICLAB_TEST`（pom 默认）。集成测试需要连接口令，不提供时相关用例跳过。不要把测试指向演示库 `FABRICLAB`：集成测试会清空并重建订单、购物车等数据。

## 验证

| 项 | 结果 |
|---|---|
| 用户端自动化测试 | 284 项通过，0 跳过 |
| 管理端自动化测试 | 138 项通过，0 跳过 |
| 浏览器端到端走查 | 50 / 50 通过，无 JS 错误和 4xx / 5xx |
| 真库验收 | AC-01 ~ AC-32 与 V01 ~ V18 对应记录齐备 |
| 并发场景 | AC-28 由两个运行中的应用验证 |

走查覆盖完整购物、支付、取消、客服联动、后台商品 / 用户 / 订单 / 客服、中英切换、注册与权限保护。AC-16 和 AC-23 只通过各自的故障注入点复现。

## 目录

| 路径 | 内容 |
|---|---|
| `fabriclab/store/` | 用户端工程、模板、样式、图片、测试 |
| `fabriclab/admin/` | 管理端工程、双语文案、后台测试 |
| `fabriclab/docs/` | 后台设计、后台视觉规范、客服设计 |
| `01-schema.sql` | 课程建表脚本（库名为 `MyPetStore`，导入时替换为 `FABRICLAB`） |
| `02-schema-with-demo.sql` | 课程对照脚本，不执行 |
| `实验一实施方案.md` | 任务分解与交付清单 |
| `MyPetStore-PRD.pdf` | 课程需求规约 |
| `MyPetstore用户端参考UI/` | 课程界面参考 |

工程代码只放在 `fabriclab/` 内；IntelliJ IDEA 项目根为 `fabriclab/`，配置位于 `fabriclab/.idea/`。

## 文档

- [用户端工程说明](fabriclab/store/README.md)
- [管理端工程说明](fabriclab/admin/README.md)
- [数据库与种子数据](fabriclab/store/src/main/resources/db/README.md)
- [后台设计](fabriclab/docs/admin-design.md)
- [客服设计](fabriclab/docs/service-design.md)
- [实验实施方案](实验一实施方案.md)
