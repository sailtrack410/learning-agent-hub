# AI 基座与知识库管理 API 文档

| 项目 | 内容 |
|---|---|
| 文档版本 | 1.0 |
| 更新日期 | 2026-07-19 |
| 适用范围 | 教育运营管理端 AI 基座与知识库 |
| 权限 | `ADMIN_AGENT_MANAGE`、`ADMIN_RAGFLOW_MANAGE` |
| 响应格式 | `R<T>`：`code`、`message`、`data`、`requestId` |

## 1. AI 基座接口

基础路径：`/api/admin/agents`。所有接口要求管理员登录和 `ADMIN_AGENT_MANAGE`。

### 1.1 模型供应商

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/ai-providers` | 查询供应商列表和最近测试状态 |
| `POST` | `/ai-providers` | 新增供应商 |
| `PUT` | `/ai-providers/{providerId}` | 更新供应商；不传新 Key 时保留原配置 |
| `DELETE` | `/ai-providers/{providerId}` | 逻辑删除供应商 |
| `POST` | `/ai-providers/{providerId}/test` | 测试供应商连通性 |
| `GET` | `/ai-providers/{providerId}/models` | 从供应商发现可选模型 |

新增或更新供应商的核心字段：

```json
{
  "providerType": "openai-compatible",
  "displayName": "环境变量模型服务",
  "baseUrl": "<OpenAI兼容服务根地址>",
  "apiKey": "<私有密钥>",
  "enabled": true
}
```

`providerType`、`displayName` 必填。响应使用 `apiKeyConfigured` 表示是否已有凭证，不返回 API Key 原文。

### 1.2 模型目录

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/llm-models` | 查询模型目录、能力和测试状态 |
| `POST` | `/llm-models` | 新增模型 |
| `PUT` | `/llm-models/{modelId}` | 更新模型 |
| `DELETE` | `/llm-models/{modelId}` | 逻辑删除模型 |
| `POST` | `/llm-models/test` | 保存前使用请求中的配置测试 |
| `POST` | `/llm-models/{modelId}/test` | 测试已保存模型 |

模型类型支持 `TEXT`、`EMBEDDING`、`RERANK`、`TTS`、`VISION`、`OCR`。核心请求示例：

```json
{
  "provider": "openai-compatible",
  "modelType": "TEXT",
  "modelName": "<供应商模型ID>",
  "displayName": "默认文本模型",
  "endpoint": null,
  "apiKey": null,
  "defaultFlag": true,
  "streamOutputFlag": true,
  "visionFlag": false,
  "toolCallFlag": false,
  "structuredOutputFlag": true,
  "reasoningFlag": false,
  "status": "ACTIVE"
}
```

模型的 `endpoint`、`apiKey` 为空时继承同 `provider` 的启用供应商配置。响应同样只返回 `apiKeyConfigured`，不回显 Key。每种模型类型只保留一个默认模型，设置新默认模型会清除同类型其他默认标记。

### 1.3 运行时选择规则

1. 资源任务优先查询启用供应商下 `ACTIVE` 且 `defaultFlag=true` 的 `TEXT` 模型。
2. 任务启动时固定端点、模型名称、能力和配置来源快照；同一任务五个角色不会中途切换模型。
3. 如果数据库中存在默认文本模型但端点、凭证或模型名不完整，任务返回配置缺失，不静默跳过错误记录。
4. 只有数据库中不存在可用默认文本模型时，才回退私有 `.env` 中启用且完整的 `AGENT_MODEL_*`。
5. 环境回退不把端点或密钥写入任务记录，任务只保存模型名称、状态和耗时。

比赛部署可以先通过 `.env` 验证模型，再由管理员把同一供应商和模型登记到 AI 基座。真实密钥只能存在部署主机或受权限保护的数据库，不进入源码、公开文档、截图或归档。

## 2. 知识库管理接口

基础路径：`/api/admin/ragflow`。所有接口要求管理员登录和 `ADMIN_RAGFLOW_MANAGE`。

### 2.1 全局设置

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/settings` | 查询 OCR、Embedding、Rerank 和分块检索配置 |
| `PUT` | `/settings` | 更新全局配置 |

配置可引用 AI 基座中的 `ocrModelId`、`embeddingModelId`、`rerankModelId`，也兼容独立服务地址和凭证。响应通过 `ocrApiKeyConfigured`、`embeddingApiKeyConfigured`、`rerankApiKeyConfigured` 表示凭证状态，不回显 Key。

约束：`chunkSize` 为 100～8000，`chunkOverlap` 为 0～2000，`retrievalTopK` 为 1～50；Embedding 维度必须为正数。

### 2.2 知识空间

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/spaces?pageNo=1&pageSize=20&keyword=` | 分页查询空间 |
| `POST` | `/spaces` | 新建空间 |
| `GET` | `/spaces/{spaceId}` | 查询空间详情 |
| `PUT` | `/spaces/{spaceId}` | 更新空间 |
| `DELETE` | `/spaces/{spaceId}` | 删除符合条件的空间 |
| `GET` | `/spaces/{spaceId}/acl` | 查询空间访问控制 |
| `PUT` | `/spaces/{spaceId}/acl` | 保存空间访问控制 |

新建空间的 `spaceName`、`spaceCode` 必填。可见范围支持 `PRIVATE`、`ROLE`、`DEPARTMENT`、`POSITION`、`PUBLIC`，状态支持 `ACTIVE`、`INACTIVE`。空间可覆盖全局 Embedding、Rerank、分块大小、重叠长度、Top K 和分数阈值。

### 2.3 文档上传与入库

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/files/upload-tickets` | 创建两阶段上传票据，`ownerType` 必须为 `RAGFLOW_DOCUMENT` |
| `POST` | `/files/confirm-upload` | 确认文件对象和附件引用 |
| `GET` | `/spaces/{spaceId}/documents` | 查询空间文档和入库进度 |
| `POST` | `/spaces/{spaceId}/documents` | 使用 `attachmentRefId` 添加文档并启动入库 |
| `POST` | `/spaces/{spaceId}/documents/{documentId}/reingest` | 对失败或需更新的文档重新入库 |
| `DELETE` | `/spaces/{spaceId}/documents/{documentId}` | 删除文档关系和受控检索数据 |

添加文档请求：

```json
{
  "attachmentRefId": "<已确认的附件引用ID>"
}
```

文档响应包含解析器、内容哈希、`ingestStatus`、任务状态、阶段、进度百分比、向量状态、切片数和可理解的失败信息。前端应轮询持久化状态，不能仅依赖页面内临时进度。

### 2.4 检索测试

`POST /spaces/{spaceId}/search-test`

```json
{
  "query": "Servlet 生命周期"
}
```

查询必填且不超过 500 字。响应命中项包含 `chunkId`、`documentId`、文档标题、切片序号、内容、页码和分数。没有可用 Embedding、空间不可用或文档未完成入库时应返回明确错误，不能伪造空成功结果。

## 3. 权限与安全约定

- 未登录返回 401；已登录但缺少对应管理权限返回 403。
- 列表和详情响应不包含任何模型密钥；仅返回是否已配置。
- 删除供应商、模型、空间和文档均由后端检查引用关系与状态，前端确认弹窗不能替代后端校验。
- 文件上传校验场景、扩展名、MIME、大小、票据所有者和对象哈希。
- 模型连通测试错误对管理员返回可理解信息，不返回请求头、Key、SQL 或服务端堆栈。
- 管理数据库含模型运行所需凭证字段，数据库账号和备份必须最小权限并限制访问。

## 4. 核心测试要点

- 供应商和模型新增、更新、删除、发现模型、连通成功与失败。
- 同类型唯一默认模型、供应商继承、默认 `TEXT` 优先和环境回退。
- Key 不回显、更新不传新 Key 时保留原配置、无权限和跨端访问。
- 全局设置范围校验，空间编码唯一、状态和可见范围校验。
- 上传票据、确认上传、添加文档、阶段进度、失败重试和删除限制。
- Embedding 缺失、外部模型失败、文档不存在、空间不存在和检索无命中。
- 检索结果包含真实文档与切片引用，资源生成引用可以回溯。

## 5. 实现依据

- `AdminAgentController`、`AgentService`、`AgentRuntimeModelConfigService`、`AgentRepository`。
- `AdminRagflowController`、`RagflowAdminService`、`RagflowRepository`。
- `AdminPermissionFilter` 中的 `ADMIN_AGENT_MANAGE`、`ADMIN_RAGFLOW_MANAGE` 路径权限。
