# module-learningresource 个性化学习资源接口

## 一、能力范围

个性化学习资源模块面向已登录学生和课程教师。学生选择已发布课程的薄弱知识点后，系统在后台依次执行画像分析、路径规划、内容生成、练习生成和事实校验 5 个固定角色，并生成以下 5 类资源：

- 讲解文档 `EXPLANATION`
- 思维导图 `MIND_MAP`
- 练习题 `EXERCISE`
- 代码实操 `CODE_LAB`
- 拓展阅读 `EXTENDED_READING`

所有资源保留课程、章节、知识点、画像版本、模型、任务耗时、智能体轨迹、事实校验结论和 RAG 引用。未经教师发布的资源不向学生展示。

## 二、学生接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/employee/learning-resources/tasks` | 幂等创建资源生成任务 |
| `GET` | `/api/employee/learning-resources/tasks` | 查询本人任务，可按 `courseId` 筛选 |
| `GET` | `/api/employee/learning-resources/tasks/{taskId}` | 查询本人任务、步骤和已生成资源 |
| `GET` | `/api/employee/learning-resources/tasks/{taskId}/events` | SSE 接收任务阶段、进度和当前角色正文增量 |
| `POST` | `/api/employee/learning-resources/tasks/{taskId}/retry` | 重试本人失败任务 |
| `GET` | `/api/employee/learning-resources` | 查询本人已发布资源，可按课程和类型筛选 |
| `GET` | `/api/employee/learning-resources/{resourceId}` | 查询本人已发布资源详情与引用 |
| `POST` | `/api/employee/learning-resources/{resourceId}/view` | 记录本人打开资源的首次、最近时间和累计查看次数 |
| `GET` | `/api/employee/learning-resources/{resourceId}/feedback` | 查询本人对该资源的已有反馈 |
| `POST` | `/api/employee/learning-resources/{resourceId}/feedback` | 保存或修改本人反馈并更新画像资源偏好 |

创建请求：

```json
{
  "courseId": "8100001",
  "knowledgePointId": "8120001",
  "idempotencyKey": "resource-20260713-001",
  "requestMessage": "请结合课程资料帮我补齐 HTTP 请求与响应基础。"
}
```

`idempotencyKey` 最长 64 字符，并与学生绑定。同一幂等键使用不同课程或知识点时返回 `409`。补充学习诉求最长 500 字符，服务端会脱敏并拦截提示词注入或越权工具调用指令。

资源反馈支持 `HELPFUL`、`TOO_DIFFICULT`、`TOO_EASY`、`NOT_RELEVANT`。同一学生对同一资源只保留一条反馈；完全相同的重复提交返回原记录，不增加画像版本，反馈发生变化时才生成新的 `RESOURCE_FEEDBACK` 画像版本和证据。反馈说明最长 500 字符，入库和写入画像前执行隐私脱敏与危险指令拦截。

资源生成启动时优先使用管理端配置的启用默认文本模型，并为整次任务固定模型配置快照；没有可用管理端默认文本模型时回退 `AGENT_MODEL_*` 部署参数。任务和步骤只记录模型名称，不保存端点或密钥。

## 三、教师接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/employee/teacher/learning-resources/tasks` | 查询本人参与课程的生成任务 |
| `GET` | `/api/employee/teacher/learning-resources/tasks/{taskId}` | 查询任务和五角色执行轨迹 |
| `GET` | `/api/employee/teacher/learning-resources` | 按课程、知识点、类型和发布状态筛选资源 |
| `GET` | `/api/employee/teacher/learning-resources/{resourceId}` | 查询资源正文、校验和引用 |
| `PUT` | `/api/employee/teacher/learning-resources/{resourceId}` | 修改标题、摘要和正文，修改后重置审核状态 |
| `POST` | `/api/employee/teacher/learning-resources/{resourceId}/review` | 通过或拒绝待审核资源 |
| `POST` | `/api/employee/teacher/learning-resources/{resourceId}/publish` | 发布已通过审核的资源 |
| `POST` | `/api/employee/teacher/learning-resources/{resourceId}/withdraw` | 撤回已发布资源 |
| `POST` | `/api/employee/teacher/learning-resources/{resourceId}/pushes` | 将已发布资源定向推送给学生或班级 |

教师只能管理自己创建或通过邀请参与的课程资源。拒绝必须填写原因；已发布资源需先撤回才能修改。

推送请求使用 `targetType=STUDENT/CLASS` 和 `targetId`。学生目标必须是有效学生，班级目标必须存在且至少包含一名有效学生。相同资源和目标重复推送时恢复原记录为 `ACTIVE`，不创建重复记录。学生资源列表与详情同时返回本人生成并已发布的资源、本人定向推送资源和所属班级推送资源。

## 四、管理员内容安全接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET` | `/api/admin/content-safety` | `ADMIN_CONTENT_SAFETY_MANAGE` | 查询提示词注入拦截、危险标记和教师复核阈值 |
| `PUT` | `/api/admin/content-safety` | `ADMIN_CONTENT_SAFETY_MANAGE` | 按版本号更新内容安全策略并写审计日志 |

更新请求：

```json
{
  "promptInjectionDetectionEnabled": true,
  "reviewConfidenceThreshold": 0.75,
  "blockedMarkers": ["忽略之前", "调用工具", "drop table"],
  "expectedVersion": 1
}
```

启用提示词注入拦截时至少保留一条危险指令标记。`expectedVersion` 与当前版本不一致时返回 `409`，避免多个管理员同时修改造成静默覆盖。策略保存后只影响新任务，不改写历史任务、资源或审核记录。

## 五、任务状态与运行规则

- 任务状态：`PENDING` -> `RUNNING` -> `SUCCEEDED` / `FAILED`。
- 任务在后台线程执行，页面关闭或刷新不会取消任务。
- 每个角色记录执行顺序、输入摘要、输出摘要、模型、耗时和失败原因；运行中保存最长 12,000 字符的正文增量，完成后清除增量并保留结构化输出摘要。
- 学生只能重试本人的 `FAILED` 任务，重试递增 `attemptNo`，不删除历史步骤。
- 客户端优先通过 SSE 更新任务和正文增量；连接失败时降级为定时查询，刷新后可从步骤 `liveOutput` 恢复当前内容。

## 六、校验、引用与发布

事实校验返回 `PASSED`、`NEEDS_REVIEW` 或 `REJECTED`。全局置信度低于管理员配置的教师复核阈值时，即使模型返回通过，系统也会降级为需复核。每条资源返回引用文档、切片、摘录、页码和相关度。

资源发布状态为 `DRAFT`、`PUBLISHED`、`WITHDRAWN` 或 `REJECTED`。学生接口只返回本人的 `PUBLISHED` 资源。

## 七、数据表

- `edu_resource_generation_task`：学生、课程、知识点、画像快照、幂等键、进度、模型和耗时。
- `edu_resource_generation_step`：五角色执行轨迹、运行中正文增量和历史重试步骤。
- `edu_generated_resource`：五类资源正文、校验和发布状态。
- `edu_generated_resource_citation`：资源与知识库文档切片的引用关系。
- `edu_content_safety_policy`：注入拦截开关、危险标记、教师复核阈值和乐观锁版本。
- `edu_generated_resource_push`：已发布资源与学生或班级推送目标的幂等关系。
- `edu_resource_learning_record`：学生查看资源的首次时间、最近时间和累计次数。

资源任务表由 `V64__add_personalized_learning_resources.sql` 创建，内容安全策略和管理权限由 `V65__add_education_content_safety_policy.sql` 创建，资源推送与使用记录由 `V66__add_teacher_learning_analytics_and_resource_push.sql` 创建，步骤正文增量由 `V71__add_learning_resource_step_live_output.sql` 增加。
