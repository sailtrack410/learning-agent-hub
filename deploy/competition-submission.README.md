# Learning Agent Hub 比赛提交版

Learning Agent Hub 是面向高校个性化学习的桌面 Web 系统。系统由 Spring Boot 后端、教育运营管理端和学生/教师端组成，首门完整示范课程为《Java Web 应用开发》。

## 1. 目录说明

```text
competition-submission/
├── server/        后端源码和可执行 JAR
├── admin-web/     教育运营管理端源码和生产静态文件
├── employee-web/ 学生/教师端源码和生产静态文件
├── docs/          正式 Word 文档与演示 PPT
├── .env.example   脱敏环境变量样板
└── README.md      本说明
```

## 2. 运行环境

请先安装并启动：

- JDK 17
- MySQL 8.0
- Redis 6 或 7
- Node.js 22 与 npm 10（从源码启动前端时需要）

可选环境：LibreOffice 用于 Office 文档预览；OpenAI 兼容模型服务用于画像、学习路径和多智能体资源生成；SMTP 用于邮箱验证码。

## 3. 准备配置

先在比赛目录根部复制配置样板：

```bash
cp .env.example .env
```

编辑 `.env`，至少填写 MySQL、Redis 和本地文件目录配置：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
WORKFORCE_FILE_LOCAL_ROOT
```

请提前创建空的 MySQL 数据库和对应账号。后端第一次启动时会通过 Flyway 自动建表并迁移到最新 V72，不需要手工导入 SQL。

`.env` 只能保存在部署机器上，不要上传、提交或发送真实密码和模型密钥。

## 4. 启动后端

```bash
cd server
java -jar learning-agent-hub-server.jar
```

启动成功后访问健康检查：

```text
http://127.0.0.1:8080/actuator/health
```

返回状态 `UP` 表示后端可用。

## 5. 启动两个 Web 端

新开一个终端启动教育运营管理端：

```bash
cd admin-web/source/admin-web
npm ci
npm run dev
```

再开一个终端启动学生/教师端：

```bash
cd employee-web/source/employee-web
npm ci
npm run dev
```

访问地址：

- 教育运营管理端：`http://127.0.0.1:5173/`
- 学生/教师端：`http://127.0.0.1:5174/`
- 后端：`http://127.0.0.1:8080/`

## 6. 首次管理员与 AI

全新数据库首次启动前，可在私有 `.env` 中临时设置：

```text
EDUCATION_ADMIN_BOOTSTRAP_ENABLED=true
EDUCATION_ADMIN_LOGIN=<管理员登录名>
EDUCATION_ADMIN_PASSWORD=<8到64位强密码>
```

管理员登录成功后，把 `EDUCATION_ADMIN_BOOTSTRAP_ENABLED` 改回 `false` 并重启后端。

需要使用智能生成时，在管理端“AI 基座”中配置启用的默认 `TEXT` 模型。也可以在管理端尚未配置默认文本模型时，通过私有 `.env` 中的 `AGENT_MODEL_*` 提供回退模型。真实 API Key 不得写入源码或公开材料。

## 7. 提交文档

`docs/` 中放置比赛正式 Word 文档和演示 PPT，包括产品功能介绍、产品设计说明书、测试说明书、部署说明书等材料；同时保留系统测试报告、开源软件与许可证清单、AI-Coding 工具使用说明三份技术附件。四份文档的 Markdown 源稿、图片源文件和编辑器配置不属于比赛提交内容。
