# module-assessment 学习评估测试用例

| 编号 | 场景 | 预期结果 |
|---|---|---|
| `ASSESSMENT-001` | 未登录查询测验 | 返回 `401` |
| `ASSESSMENT-002` | 教师账号查询或提交学生测验 | 返回 `403` |
| `ASSESSMENT-003` | 学生查询课程测验列表 | 只返回已发布课程和已发布测验 |
| `ASSESSMENT-004` | 查询测验详情 | 返回单选、多选和判断题，不泄露正确答案 |
| `ASSESSMENT-005` | 提交三类题型的正确和错误答案 | 得分、正误、错误类型和解析正确 |
| `ASSESSMENT-006` | 提交空答案 | 测验可提交，所有题记为未作答 |
| `ASSESSMENT-007` | 部分作答且多选题漏选 | 未提交题目按未作答记录，漏选标记为 `PARTIAL_SELECTION` |
| `ASSESSMENT-008` | 提交越权题目、重复题目或无效选项 | 返回 `400`，不产生作答记录 |
| `ASSESSMENT-009` | 使用相同幂等键重复提交 | 返回原作答 ID，作答次数不增加 |
| `ASSESSMENT-010` | 两名学生提交同一测验 | 作答、掌握度和画像数据互相隔离 |
| `ASSESSMENT-011` | 测验提交成功 | 记录总用时、单题用时和尝试次数，更新知识点掌握度 |
| `ASSESSMENT-012` | 测验触发画像联动 | 画像版本递增，新增掌握程度、易错点和两条评估证据 |
| `ASSESSMENT-013` | 学生端完成测验 | 显示得分、阶段报告、知识点掌握度和逐题解析 |
| `ASSESSMENT-014` | 1440px 桌面 Web 实测 | 无横向溢出，答题、提交和解析可操作，控制台无异常 |
| `ASSESSMENT-015` | 测验提交后再次查询学习路径 | 路径版本递增，保存来源作答和最新画像版本，节点状态与推荐原因按掌握度调整 |

自动化覆盖位于 `ModuleAssessmentApiIntegrationTest`、`ModuleLearningPathApiIntegrationTest`、`ModuleStudentProfileApiIntegrationTest` 和 `employee.test.ts`。
