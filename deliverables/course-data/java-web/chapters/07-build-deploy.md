# 第 7 章 构建与部署

关联知识点：`BUILD`、`DEPLOY`

## 学习目标

- 理解 Maven 生命周期、依赖范围和可执行产物。
- 使用环境变量管理不同环境配置。
- 通过健康检查、日志和回滚完成安全发布。

## 核心讲义

Maven 的 `test` 验证测试，`package` 生成产物，`verify` 执行更完整的校验。依赖版本应集中管理，不能依赖本机偶然存在的文件。前端生产构建还应执行类型检查和资源打包。

部署配置与代码分离，数据库密码、模型 Key 等从环境变量读取。发布前执行数据库增量迁移和完整测试；应用启动后检查健康接口、关键 API 和前端控制台；出现问题应使用可验证的上一版本回滚，而不是直接修改生产数据。

## 练习

1. `mvn test` 与 `mvn package` 的区别是什么？
2. 为什么不能把数据库密码写进 `application.yml`？
3. 发布后至少检查哪些信号？

## 参考答案

前者执行测试，后者在生命周期中完成测试并生成产物；源码可能泄露且环境无法隔离；健康状态、错误日志、数据库迁移、关键接口和前端资源。

## 代码案例

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

尝试补充：编写部署检查表，包含备份、迁移、启动、健康检查、冒烟测试和回滚条件。
