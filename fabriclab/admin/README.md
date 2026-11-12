# fabriclab-admin

FABRICLAB 后台管理端工程，端口 8081，与用户端（`fabriclab/store`，8080）独立部署、共享同一数据库。当前阶段：T12 后台设计说明与管理端初始化。

设计依据见仓库 `docs/admin-design.md`：角色、用例、模块划分、页面流程、字段与状态、权限矩阵。

## 结构

```
src/main/java/com/fabriclab/admin/
├── AdminApplication.java                启动类（8081）
├── config/MybatisPlusConfig.java        分页插件（数据库 LIMIT 分页）
├── config/WebMvcConfig.java             拦截器装配：CSRF 全 POST 校验、/admin/** 统一访问检查
├── controller/AdminSignonController.java 后台登录 /admin/signon 与退出 /admin/signout
├── controller/ConsoleController.java    控制台 /admin/console、根路径跳转、健康检查
├── controller/AdminModelAdvice.java     全局模型：CSRF 令牌、品牌、当前管理员身份
├── controller/GlobalExceptionHandler.java 400 / 403 / 404 / 500 统一错误页
├── security/CsrfService.java            后台会话级 CSRF 令牌（键 adminCsrfToken）
├── security/CsrfInterceptor.java        全部 POST 写请求令牌校验
├── security/AdminAuthInterceptor.java   访问检查：未登录跳转、每次请求查库复核 ADMIN 与启用状态
├── service/AdminAuthService.java        登录：四类失败统一提示、会话标识更换、后台内返回页白名单
├── service/AdminSessionService.java     会话身份读写（admin 前缀属性键）与登录时间
├── service/ConsoleService.java          控制台模块范围（不输出未经统计的数字）
├── service/BrandService.java            品牌展示信息
├── model/AdminUser.java                 登录后的最小身份（不含口令散列）
├── model/AdminModule.java               后台模块入口（名称、路径、范围、需求、是否已交付）
├── exception/                           业务 400 / 不存在 404 / CSRF 403
├── entity/StoreUser.java                与用户端同库同表的账号实体
└── mapper/StoreUserMapper.java          对应 Mapper
src/main/resources/application.yml       端口 8081、会话 Cookie ADMINSESSION、数据源
src/main/resources/templates/            signon 登录 / console 控制台 / error 错误 / fragments 片段
src/main/resources/static/               admin.css、品牌标志
```

## 数据层约定

- 与用户端共享同一 `MyPetStore` 库与同一份 DDL（仓库根目录 `01-schema.sql`），后台不建表、不清库、不加字段。
- 连接参数走环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`，口令不入仓库。
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
| `http://localhost:8081/health` | `{"status":"UP","app":"fabriclab-admin","stage":"T12"}` |

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
