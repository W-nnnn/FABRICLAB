# fabriclab-store

FABRICLAB 用户端工程，端口 8080。当前阶段：T17 服装类扩展（可选交付已实施）。

## 结构

```
src/main/java/com/fabriclab/store/
├── StoreApplication.java            启动类
├── config/MybatisPlusConfig.java    分页插件（数据库 LIMIT 分页）
├── config/WebMvcConfig.java         拦截器装配：CSRF 全 POST 校验、受保护入口访问检查
├── controller/MainController.java   首页（新品上架 ≤3、精选推荐 ≤4）与健康检查
├── controller/BrowseController.java 商品列表 /category：GET 整页刷新
├── controller/ProductController.java 商品详情 /product?id=：仅上架可看
├── controller/RegisterController.java 会员注册 /register：GET 表单、POST 提交
├── controller/SignonController.java 登录 /signon 与退出 /signout（POST + CSRF）
├── controller/CartController.java 购物车 /cart 与加入、改量、移除
├── controller/CheckoutController.java 结算确认 /checkout 与提交 /checkout/submit
├── controller/OrderController.java 我的订单 /orders、订单详情、取消待支付订单
├── controller/PaymentController.java 模拟支付页 /orders/{id}/payment 与成功、失败两个动作
├── controller/CurrentUserAdvice.java 全局模型：CSRF 令牌与当前登录身份
├── controller/GlobalExceptionHandler.java 400 / 403 / 404 / 500 统一错误页
├── security/CsrfService.java        会话级 CSRF 令牌：生成与校验
├── security/CsrfInterceptor.java    全部 POST 写请求令牌校验
├── security/AuthInterceptor.java    受保护入口：未登录跳转、角色与启用状态每次请求查库复核
├── service/BrandService.java        品牌展示信息
├── service/CategoryService.java     两级分类：编码唯一、同级名唯一、二级必须挂一级
├── service/ProductService.java      单 SKU 商品：款号唯一、必归二级分类、version 递增
├── service/BrowseService.java       浏览：分类作用域、名称包含匹配、排序白名单、分页
├── service/DetailService.java       详情：ID 定位、相册升序、封面回退、分类路径
├── service/CaptchaService.java      服务端验证码：一次性消费、5 分钟有效、可注入时钟
├── service/RegistrationService.java 注册：仅 CUSTOMER/ENABLED、用户名唯一、BCrypt 存储
├── service/AuthService.java         登录：四态统一失败、会话标识更换、站内返回页白名单
├── service/CartService.java         持久购物车：存库不存 Session、同款累加、版本与归属校验、失效判定
├── service/CheckoutService.java     结算确认快照与整车下单事务（订单 + 明细 + 扣库存 + 清车）
├── service/OrderService.java        本人订单列表、详情快照与取消恢复库存
├── service/PaymentService.java      教学模拟支付：条件更新成组写付款信息、重复成功保首次凭证、失败不写库
├── service/CancelFaultPoint.java    取消恢复库存故障注入位（AC-23 验收用，生产为空实现）
├── service/CheckoutFaultPoint.java  下单故障注入位（AC-16 验收用，生产为空实现）
├── service/CurrentUserService.java  会话身份读取、数据归属校验（requireOwner → 404）
├── model/CategoryType.java          类型三值由分类编码后缀映射（*_GAR / *_ACC / 其余 ALL）
├── model/CurrentUser.java           登录后的最小身份（不含密码散列与角色原文）
├── model/BrowseQuery.java           列表参数解析：页码归一、页大小 1—50、关键字 ≤120
├── model/Quantity.java              购买数量：缺省 1，仅接受 1—99 整数
├── model/PasswordPolicy.java        注册密码：8—32 个字符且至少两类
├── model/OrderStatus.java           订单五状态与界面名称
├── model/OrderQuery.java            订单参数：五状态筛选、完整订单号、页码与页大小
├── model/ReceiverInfo.java          收货三项去空白后必填与长度上限
├── model/CheckoutConfirmation.java  服务端临时结算确认（条目 ID、数量、版本与单价）
├── model/SortOption.java            排序四值白名单：DEFAULT / PRICE_ASC / PRICE_DESC / NEWEST
├── model/SingleSkuText.java         尺码配色并入名称、面料档案纯文本落点
├── exception/BusinessException.java 业务校验异常 → HTTP 400
├── exception/NotFoundException.java 资源不存在 → HTTP 404（标题可定制，如商品不存在或已下架）
├── exception/CsrfViolationException.java 无效表单防护请求 → HTTP 403
├── entity/                          七张业务表实体
│   StoreUser / Category / Product / ProductImage
│   CartItem / SalesOrder / SalesOrderItem
└── mapper/                          对应 Mapper，继承 BaseMapper
src/main/resources/application.yml   端口、数据源、MyBatis-Plus
src/main/resources/templates/        index 首页 / category 列表 / product 详情 / cart 购物车 / checkout 结算确认 / orders 订单列表 / order 订单详情 / payment 模拟支付 / signon 登录 / register 注册 / error 错误 / fragments 片段
src/test/java/com/fabriclab/store/
├── controller/MainControllerTest.java   首页渲染（Mock 服务层）
├── controller/BrowseControllerTest.java 列表 400 / 404 / 条件回显
├── controller/ProductControllerTest.java 详情渲染、ID 400、不可用 404、售罄展示
├── controller/RegisterControllerTest.java 注册渲染、成功、失败重渲、密码不回显
├── controller/SignonControllerTest.java 登录页渲染、登录态跳转、返回页白名单、密码不回显
├── controller/CartControllerTest.java   购物车页渲染、失效阻止结算、加入反馈、修改与移除重定向
├── controller/CheckoutControllerTest.java 确认页渲染与确认入会话、提交成功跳详情、收货校验失败保留输入
├── controller/OrderControllerTest.java  列表渲染与空状态、非法参数 400、越权 404、取消回详情
├── controller/PaymentControllerTest.java 支付页金额与两个动作、非待支付回详情、成功与重复成功提示、伪造金额无效、越权 404
├── security/AuthInterceptorTest.java    未登录跳转、禁用/改角色/删除账号拦截并失效会话
├── security/CsrfServiceTest.java        令牌生成复用、缺失/篡改拒绝
├── service/BrandServiceTest.java
├── service/CategoryServiceTest.java 两级约束、重复编码、更新不可变字段
├── service/ProductServiceTest.java  款号唯一、二级归属、version 递增
├── service/BrowseServiceTest.java   分类作用域、类型切换升级、通配符转义、两栏上限
├── service/DetailServiceTest.java   仅上架可看、相册升序与封面回退、ID 解析
├── service/CaptchaServiceTest.java  一次性消费、过期、重复使用、错误、无效
├── service/RegistrationServiceTest.java 账号口径、唯一性、验证码先于落库、BCrypt 格式
├── service/AuthServiceTest.java         登录四态统一失败、会话标识更换、返回页白名单
├── service/CartServiceTest.java         同款累加、1—99、20 种上限、库存与上架、版本与归属、失效判定
├── service/CheckoutServiceTest.java     确认只读、快照核对、状态/库存/价格变化拒绝、下单写库序列与清理校验
├── service/OrderServiceTest.java        归属 404、状态与订单号筛选、取消仅待支付且只恢复一次
├── service/PaymentServiceTest.java      归属 404、仅待支付可付、成组写付款信息、重复成功保首次凭证、失败不写库、并发未命中回读
├── service/CurrentUserServiceTest.java  会话身份读取、归属不符 404
├── model/OrderQueryTest.java        状态白名单、订单号精确匹配与上限、页码与页大小归一、收货三项校验
├── model/BrowseQueryTest.java       页码归一、页大小白名单、关键字截断拒绝
├── model/QuantityTest.java          数量缺省 1、1—99 白名单
├── model/PasswordPolicyTest.java    密码长度与两类字符、明文不入异常信息
├── model/CategoryTypeTest.java      编码后缀 → 类型三值映射
├── model/SingleSkuTextTest.java     变体名与面料档案
├── entity/EntityMappingTest.java     表名、主键、生成列、单 SKU 口径
├── BrowseIntegrationTest.java       真库首页两栏与列表验收 AC-01~03（需 MYSQL_PASSWORD）
├── DetailIntegrationTest.java       真库详情验收 AC-04：相册有序、封面回退、下架 404、售罄不扣库存
├── RegistrationIntegrationTest.java 真库注册验收：成功落库、重复用户名、非法密码、验证码错误与重复使用
├── AuthIntegrationTest.java         真库登录会话验收 AC-05~07/31：四态统一失败、禁用拦截、退出失效、CSRF 403
├── CartIntegrationTest.java         真库购物车验收 AC-08~11：累加合并、非法与第 21 种不写库、退出重登保留、跨账号 404、旧版本拒绝、失效保留与移除
├── OrderIntegrationTest.java        真库结算下单验收 AC-12~17/19/30：收货校验、金额后端计算、两明细下单、改价与改车拒绝、异常回滚、快照不变、五状态与订单号、取消恢复库存
├── PaymentIntegrationTest.java      真库支付验收 AC-18~24：失败后成功再重复成功保首次凭证、改价下架不影响金额与付款、两明细取消只恢复一次、下架取消后仍下架且不能付款、取消异常回滚、支付与取消并发只一种迁移
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
| `http://localhost:8080/product?id=1` | 商品详情：款号与 SKU、¥ 两位小数、库存或售罄、相册有序与封面回退、数量 1—99、返回列表 |
| `http://localhost:8080/register` | 会员注册：用户名唯一、密码 8—32 两类字符、服务端验证码一次性校验、BCrypt 落库、注册后自行登录 |
| `http://localhost:8080/signon` | 登录：仅启用 CUSTOMER、统一失败提示、用户名回显密码不回显、站内返回页白名单 |
| `http://localhost:8080/fabrics` | 面料档案：按纤维四类归组在售款，洗护判定与详情页同一套 |
| `http://localhost:8080/crafts` | 工艺说明：染织裁缝四段 + 当季在售条 |
| `http://localhost:8080/series/{id}` | 系列专题：一级系列按二级品类归组；非数字 400、不存在或二级 ID 404 |
| `http://localhost:8080/health` | `{"status":"UP","app":"fabriclab-store","stage":"T5"}` |

浏览口径：仅上架商品出现在动态区域，零库存仍展示为「暂时缺货」；搜索只匹配名称（去首尾空白、≤120，`%` `_` 按普通字符），不搜款号；非法参数 400，分类不存在 404「分类不可用」，技术异常 500 通用失败页。

详情口径：仅上架商品可看，下架或不存在统一 404「商品不存在或已下架」，不渲染可购买详情；零库存可查看但不可购买；相册按 sort_order、id 升序，无相册回退封面、封面缺失再用缺省封面；简介与纯文本详情转义输出、保留换行；数量输入 1—99（`Quantity` 后端校验，购物车模块接入时共用）；浏览与刷新不扣库存；无「你可能还喜欢」推荐区。

环境：JDK 25、Maven 3.9.16、Spring Boot 3.5.16、MyBatis-Plus 3.5.18、MySQL 8.4。

登录与访问口径：仅启用的 CUSTOMER 可登录用户端；不存在、错误密码、禁用、角色不符四类失败共用统一提示「账号或密码错误，或账号不可用」，用户名回显、密码不回显不记日志；登录成功更换会话标识（会话固定防护），会话只存最小身份；站内返回页仅接受本应用相对路径。退出为 POST + CSRF 状态变更，使本端会话与临时确认失效，数据库购物车不删除；用户端会话 Cookie 独立命名 `STORESESSION`，与管理端（8081）隔离。受保护入口（/cart /checkout /orders /payment /account 前缀）每次请求对照数据库复核角色与启用状态，登录后被禁用或改角色即时拦截；数据归属校验 `requireOwner` 归属不符统一 404 不泄露存在性。全部 POST 写请求校验 CSRF 令牌，缺失或篡改返回 403「无效表单防护请求」；页面输出统一转义。

扩展页口径（T17）：三个扩展页纯展示、只读在售数据，不新增表与字段、不触碰登录 / 购物车 / 订单任何路径，缺失不影响 AC-01~32。面料档案与详情页洗护建议共用 `CareGuide` 纤维归类；页脚「选购」列改为五个系列专题入口、「服务」列带面料档案与工艺说明；分类导航与既有链接不变。
