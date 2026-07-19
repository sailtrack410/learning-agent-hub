# module-assessment 学习评估接口

## 一、能力范围

学习评估首版面向已登录学生，提供已发布课程测验查询、作答提交、自动判分、知识点诊断和画像联动。《Java Web 应用开发》诊断测验包含单选、多选和判断 3 类题型。

## 二、接口清单

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| `GET` | `/api/employee/courses/{courseId}/assessments` | 查询课程已发布测验摘要 | 已登录学生 |
| `GET` | `/api/employee/courses/{courseId}/assessments/{assessmentId}` | 查询题目和选项，提交前不返回正确答案 | 已登录学生 |
| `POST` | `/api/employee/courses/{courseId}/assessments/{assessmentId}/attempts` | 提交作答并返回判分、解析、诊断和画像更新结果 | 已登录学生 |

## 三、提交请求

```json
{
  "idempotencyKey": "0f4507fe-d1ed-497f-981f-516cb821d1f4",
  "durationSeconds": 180,
  "answers": [
    {
      "questionId": "8510001",
      "selectedValues": ["B"],
      "durationSeconds": 30
    }
  ]
}
```

约束：

1. `idempotencyKey` 最长 64 个字符，同一学生重复提交同一键时返回原作答结果。
2. 空答案和部分作答可以提交，未作答题按错题记录。
3. 题目不属于当前测验、同题重复、无效选项或单选/判断题选择多项时返回 `400`。
4. 总用时和单题用时范围为 0 至 86400 秒。

## 四、判分与联动规则

- 单选、判断和多选均要求答案集完全匹配才得分。
- 错误类型包含未作答、多选漏选、正错混选和选项错误。
- 知识点掌握度按历次相关题的正确率累计，保留作答次数和正确次数。
- 每次有效提交生成 `ASSESSMENT` 类型画像版本，更新“掌握程度”和“易错点”，置信度为 `0.90`。
- 测验提交完成后，评估结果成为学习路径输入：旧活动路径转为历史版本，并使用最新画像版本、知识点掌握度和已有学习进度生成新路径。

## 五、数据表

- `edu_assessment`：测验主表。
- `edu_assessment_question`：题目、选项、正确答案、解析和分值。
- `edu_assessment_attempt`：学生测验作答、幂等键、尝试次数、得分和总用时。
- `edu_assessment_answer`：逐题答案、得分、错误类型和用时。
- `edu_student_mastery`：学生课程内的知识点累计掌握度。

数据库结构和示范测验由 `V62__add_learning_assessments.sql` 创建。
