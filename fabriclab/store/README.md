# fabriclab-store

FabricLab-Atelier 用户端工程，端口 8080。当前阶段：T1 项目初始化。

## 结构

```
src/main/java/com/fabriclab/store/
├── StoreApplication.java       启动类
├── controller/MainController.java   首页与健康检查
└── service/BrandService.java        品牌展示信息
src/main/resources/
├── application.yml
└── templates/index.html
src/test/java/com/fabriclab/store/
├── controller/MainControllerTest.java
└── service/BrandServiceTest.java
```

Mapper 层与数据源在 T2 接入；本阶段不配置数据库、不写业务功能、不放静态商品或统计假数据。

## 构建与验证

```bash
mvn test
mvn spring-boot:run --server.port=8080
```

宿主环境若注入 `SERVER__PORT`，会覆盖配置文件端口；显式指定 `--server.port=8080` 可保证落在 8080。

| 地址 | 结果 |
|---|---|
| `http://localhost:8080/main` | 品牌占位模板 |
| `http://localhost:8080/health` | `{"status":"UP","app":"fabriclab-store","stage":"T1"}` |

环境：JDK 17、Maven 3.9.16、Spring Boot 3.5.16。
