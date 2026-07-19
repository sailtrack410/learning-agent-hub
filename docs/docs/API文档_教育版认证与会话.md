# 教育版认证与会话 API 文档

| 项目 | 内容 |
|---|---|
| 文档版本 | 1.0 |
| 更新日期 | 2026-07-19 |
| 适用范围 | 学生、教师、教育运营管理员桌面 Web |
| 响应格式 | `R<T>`：`code`、`message`、`data`、`requestId` |

## 1. 身份边界

- 学生和教师使用业务账号体系，接口前缀保留为 `/api/employee/auth`，登录后由 `educationRole=STUDENT/TEACHER` 决定前端工作区。
- 教育运营管理员使用独立账号体系，接口前缀为 `/api/admin/auth`。
- 两类账号使用不同认证入口；业务会话访问 `/api/admin/**` 或管理员会话访问 `/api/employee/**` 均被后端拒绝。
- 登录成功后由服务器签发 Session Cookie，后续请求不传递账号密码。退出登录会立即使当前 Session 失效。

## 2. 学生/教师认证接口

基础路径：`/api/employee/auth`

| 方法 | 路径 | 是否需登录 | 用途 |
|---|---|---:|---|
| `POST` | `/login` | 否 | 登录；按配置处理首次邮箱绑定或邮箱二次验证 |
| `POST` | `/login/send-code` | 否 | 向账号已绑定邮箱发送登录二次验证码 |
| `POST` | `/logout` | 是 | 销毁当前业务会话 |
| `GET` | `/me` | 是 | 查询当前用户、教育角色和身份摘要 |
| `POST` | `/email/send-code` | 否 | 发送绑定邮箱或重置密码验证码；受用途、邮箱格式和频率限制 |
| `POST` | `/email/bind` | 是 | 绑定并验证邮箱 |
| `POST` | `/password/reset` | 否 | 使用邮箱验证码重置密码 |

### 2.1 登录请求

```json
{
  "loginName": "<登录名或绑定邮箱>",
  "password": "<私有密码>",
  "emailCode": "<需要二次验证时填写>",
  "bindEmail": "<首次绑定时填写>"
}
```

`loginName`、`password` 必填。`emailCode` 和 `bindEmail` 只在服务端响应要求相应步骤时填写。

### 2.2 登录响应关键字段

| 字段 | 含义 |
|---|---|
| `userId` | 当前业务用户 ID；前端按字符串处理长整型 |
| `userType` | 账号大类 |
| `displayName`、`loginName` | 页面展示与登录标识 |
| `requiresEmailCode` | 为 `true` 时尚未签发会话，需要完成邮箱二次验证 |
| `maskedEmail` | 二次验证使用的脱敏邮箱 |
| `requiresEmailBinding` | 为 `true` 时需要先绑定邮箱并验证 |

### 2.3 当前用户 `/me`

响应包含 `userId`、`userType`、`displayName`、`loginName`、`email`、`roleCodes`、`permissionCodes` 和 `educationRole`。业务用户还可能包含部门、岗位、上级和账号状态摘要；教育页面以 `educationRole` 作为学生/教师入口依据。

### 2.4 验证码与密码重置

发送通用邮箱验证码：

```json
{
  "email": "user@example.com",
  "purpose": "BIND"
}
```

`purpose` 支持 `BIND`、`RESET_PASSWORD`。绑定邮箱请求为 `email + code`；重置密码请求为 `email + code + newPassword`，新密码长度为 8～64 位。

验证码默认 5 分钟有效，同一用途 60 秒内限制重复发送，验证成功后立即删除。接口响应不得回显验证码。

## 3. 管理员认证接口

基础路径：`/api/admin/auth`

| 方法 | 路径 | 是否需登录 | 用途 |
|---|---|---:|---|
| `POST` | `/login` | 否 | 管理员账号密码登录 |
| `POST` | `/logout` | 是 | 销毁当前管理员会话 |
| `GET` | `/me` | 是 | 查询管理员角色和权限码 |

管理员登录请求只使用 `loginName`、`password`。`/me` 返回的 `roleCodes`、`permissionCodes` 用于前端导航裁剪；后端仍会对每个管理请求重新鉴权。

全新数据库首次管理员通过私有 `.env` 中的 `EDUCATION_ADMIN_BOOTSTRAP_*` 初始化。该机制只在 `admin_user` 为空时生效，成功登录后必须关闭引导开关。

## 4. 错误与安全约定

| 场景 | 预期结果 |
|---|---|
| 未登录访问受保护接口 | HTTP 401 |
| 已登录但跨端或无权限 | HTTP 403 |
| 必填、邮箱格式或密码长度不合法 | 参数校验失败，不创建或修改业务数据 |
| 登录名或密码错误 | 返回统一业务提示，不暴露账号是否存在 |
| 连续登录失败达到阈值 | 临时锁定，成功登录后清除失败计数 |
| Session 失效 | 前端清理本地身份状态并返回登录页 |

密码只以 BCrypt 哈希保存；日志、文档和响应不得记录密码、验证码、Session ID 或完整个人隐私数据。

## 5. 核心测试要点

- 学生、教师和管理员成功登录、查询 `/me`、退出后会话失效。
- 错误密码、空字段、非法邮箱、短密码和过期/错误验证码。
- 未登录、业务会话访问管理端、管理员会话访问业务端。
- `STUDENT`、`TEACHER` 角色路由与后端角色校验一致。
- 连续失败锁定、邮箱发送频率、验证码一次性消费。
- 全新库管理员引导只能创建首个管理员，不能重置或提权既有账号。

## 6. 实现依据

- `AdminAuthController`、`EmployeeAuthController`。
- `LoginService`、`AccountService`、`EmailCodeService`、`AuthLockService`。
- `SecurityConfig`、业务/管理身份 Provider、`UserTypePathFilter`。
