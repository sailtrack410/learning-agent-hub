# module-learningpath 个性化学习路径测试用例

| 编号 | 场景 | 预期结果 |
|---|---|---|
| `LEARNING-PATH-001` | 未登录查询路径 | 返回 `401` |
| `LEARNING-PATH-002` | 教师账号查询或完成学生路径节点 | 返回 `403` |
| `LEARNING-PATH-003` | 学生首次查询已发布课程路径 | 生成 V1，记录当前画像版本、生成原因和一个活动路径 |
| `LEARNING-PATH-004` | 两名学生查询同一课程路径 | 路径、推荐和进度互相隔离 |
| `LEARNING-PATH-005` | 课程存在知识点依赖 | 节点顺序满足拓扑依赖，未满足前置条件的节点为 `LOCKED` |
| `LEARNING-PATH-006` | 学生越序完成节点 | 返回 `409`，路径和进度不变化 |
| `LEARNING-PATH-007` | 学生完成当前节点 | 当前节点变为 `COMPLETED`，下一可学习节点变为 `IN_PROGRESS` |
| `LEARNING-PATH-008` | 重复完成已完成节点 | 返回当前路径，不重复写学习进度 |
| `LEARNING-PATH-009` | 学生提交课程测验 | 掌握度和画像更新，旧路径变为 `SUPERSEDED`，新路径版本递增 |
| `LEARNING-PATH-010` | 测验掌握度达到 80% | 对应路径节点和推荐自动完成，完成来源为 `ASSESSMENT` |
| `LEARNING-PATH-011` | 相同幂等键重复提交测验 | 返回原作答，不重复生成路径版本 |
| `LEARNING-PATH-012` | 学生端查看路径 | 显示“今日学习”“学习路径”“推荐资源”三个入口 |
| `LEARNING-PATH-013` | 学生端查看节点 | 显示目标、资源、时长、完成条件、状态、掌握度和推荐原因 |
| `LEARNING-PATH-014` | 从路径或推荐打开课程文章 | 返回对应课程和章节，文章正常渲染 |
| `LEARNING-PATH-015` | 1280px 桌面端和 390px 移动端实测 | 无横向溢出、遮挡和控制台异常，节点完成按钮可操作 |
| `LEARNING-PATH-016` | 同一学生并发完成同一当前节点 | 两个请求均幂等返回，只完成当前节点且只解锁一个后续节点 |

后端自动化覆盖位于 `ModuleLearningPathApiIntegrationTest` 和 `ModuleAssessmentApiIntegrationTest`；学生端接口与路由覆盖位于 `employee.test.ts` 和 `router/index.test.ts`。完整交互通过真实浏览器验证。
