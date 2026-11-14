# fabriclab-admin

FABRICLAB 后台管理端，端口 8081，与用户端（`fabriclab/store`，8080）独立部署、共享同一 `MyPetStore` 库。当前阶段：T14 分类、款号、库存补货与服装相册。

设计依据：`docs/admin-design.md`（角色、用例、模块、字段与状态、权限矩阵）；视觉规范见 `docs/admin-ui-design.md` 与 `docs/admin-ui-prototype.html`。

## 结构

```
src/main/java/com/fabriclab/admin/
├── config/       分页、语言（中 / 英）、商品内容词典、拦截器装配
├── controller/   登录、控制台、分类、款号、相册、错误页
├── service/      登录会话、分类树、商品维护、增量补货、相册、控制台计数、描述编解码
├── model/        表单白名单与列表 / 相册视图
├── entity/       与用户端同库同表的实体
├── mapper/       对应 Mapper
├── security/     CSRF 与 /admin/** 访问检查
└── exception/    业务 400 / 未找到 404 / CSRF 403
src/main/resources/
├── application.yml             8081、会话 Cookie ADMINSESSION、数据源
├── templates/                  signon / console / categories / category-form / products / product-form / album / error / fragments
├── messages.properties         界面文案（messages_en.properties 为英文版）
├── content-en.properties       商品数据英译词典
├── static/css/admin.css        V3 视觉
└── static/images/products/     相册预览缩略图（320px，与用户端同名路径）
```

## 数据层约定

- 与用户端共享同一 `MyPetStore` 库与同一份 DDL（仓库根目录 `01-schema.sql`），后台不建表、不清库、不加字段。
- 连接参数走环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`，口令不入仓库。
- 相册的图类型与来源许可不新增字段，编进 `product_image.alt_text`（格式 `F|来源|替代文本`，`F` 平铺 / `W` 上身 / `D` 细节）。
- 后台账号管理只作用于 `role='CUSTOMER'` 的账号；管理员账号本身不被后台功能修改。

## 构建与验证

```bash
mvn test
mvn spring-boot:run --server.port=8081
```

宿主环境若注入 `SERVER__PORT`，会覆盖配置文件端口；显式指定 `--server.port=8081` 可保证落在 8081。

真库验收需先准备数据（空库执行 `01-schema.sql`，再执行用户端 `store/src/main/resources/db/seed-data.sql`）：

```bash
export MYSQL_PASSWORD='<本机口令>'
mvn test
```

| 地址 | 结果 |
|---|---|
| `http://localhost:8081/` | 跳转控制台；未登录再跳登录页 |
| `http://localhost:8081/admin/signon` | 后台登录：仅启用的 ADMIN、统一失败提示、账号回显口令不回显 |
| `http://localhost:8081/admin/console` | 控制台：登录身份、会话与访问规则、后台模块范围（不显示经营统计） |
| `http://localhost:8081/admin/categories` | 两级分类：新增、编辑、删除（有子分类或商品引用时拒绝） |
| `http://localhost:8081/admin/products` | 款号列表：筛选、搜索、分页、批量上下架、行菜单（相册 / 补货 / 上下架 / 删除） |
| `http://localhost:8081/admin/products/new`、`/{id}/edit` | 新增与编辑；编辑页库存只读，补货走弹窗 |
| `http://localhost:8081/admin/products/{id}/album` | 服装相册：平铺 / 上身 / 细节三组、来源与许可、设为封面、空组入口 |
| `POST http://localhost:8081/admin/products/{id}/restock` | 增量补货：正整数，按最新库存加算并递增 `version` |
| `http://localhost:8081/health` | `{"status":"UP","app":"fabriclab-admin","stage":"T14"}` |

## 库存与相册口径

- 补货为增量语义：只收 1—9999 的正整数，服务端重查最新 `stock` 与 `version` 后条件更新；期间被下单、取消或另一次补货改写时整条更新落空并提示重试，不覆盖别人的写入。
- 商品保存不接收库存，编辑页库存只读；新建时可填初始库存。
- 相册按 `sort_order`、`id` 升序出图；设为封面写 `product.cover_image` 并推 `version`；相册为空时用户端回退封面，封面为空再用缺省图。
- 用户端读 `alt_text` 时用同名规则只取替代文本，历史数据（无前缀）原样保留，不把编码写进 `alt`。
- 相册页图片为同名 320px 缩略图，仅用于后台预览；用户端仍使用原图。

## 访问与会话口径

- 后台路径统一 `/admin` 前缀；`/admin/**` 全部入口先过访问检查，登录页 `/admin/signon` 例外。
- 检查内容为「会话有身份 + 库中 `role=ADMIN` + `status=ENABLED`」；三者缺一即跳登录页，已在登录态的被停用、被改角色、被删除账号同时失效会话。
- 会话 Cookie 独立命名 `ADMINSESSION`（`HttpOnly`），端口 8081、会话属性键 `admin*` 前缀；携带用户端 `STORESESSION` 访问后台不产生任何权限。
- 登录成功更换会话标识（会话固定防护），会话只存最小身份与登录时间；口令 BCrypt 存储、不回显、不记日志。
- 退出为 POST + CSRF 状态变更，使后台会话失效，返回登录页；不影响用户端会话与数据库购物车。
- 全部 POST 校验会话级表单令牌，缺失或篡改返回 403「无效表单防护请求」。

## 控制台口径

- 不展示订单量、销售额、转化率、访客数等任何后台未真实统计的指标。
- 只呈现当前管理员身份（账号、显示名、角色、本次登录时间）、会话与访问规则、后台模块范围及其所属需求。
- 模块入口随对应任务启用；未启用入口不提供链接、不显示数量。
