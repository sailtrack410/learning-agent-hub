# module-profile 学生画像接口

## 一、能力范围

学生画像由画像主表、维度、版本、证据和对话消息组成。当前首版通过 8 轮自然语言对话采集知识基础、学习目标、掌握程度、易错点、认知风格、资源偏好、学习节奏和实践能力。每次回答、纠正或测验联动都会生成新版本和证据记录。

教师端不能读取学生完整画像；后续教师视图只提供课程范围内的脱敏摘要。

## 二、接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| `GET` | `/api/employee/student/profile` | 获取本人画像、对话和下一问题；首次访问自动初始化 | 已登录学生本人 |
| `POST` | `/api/employee/student/profile/messages` | 提交当前维度的自然语言回答 | 已登录学生本人 |
| `PUT` | `/api/employee/student/profile/dimensions/{dimensionCode}` | 主动纠正已形成的维度结论 | 已登录学生本人 |

## 三、对话规则

1. 系统按固定维度顺序一次只询问一个问题，不使用长表单。
2. 回答长度为 2 至 1000 个字符。
3. 对话回答置信度初始为 `0.75`，依据类型为 `STUDENT_DIALOGUE`。
4. 学生主动纠正后的置信度为 `0.95`，依据类型为 `STUDENT_CORRECTION`。
5. 完成 8 个维度后继续提交对话返回 `409`，后续更新使用纠正接口或评估联动。
6. 测验提交后使用 `ASSESSMENT` 触发类型更新掌握程度和易错点，置信度为 `0.90`，证据类型为 `ASSESSMENT_RESULT`。

## 四、数据表

- `edu_student_profile`：学生当前画像和版本号。
- `edu_student_profile_dimension`：8 个维度的当前结论、置信度、依据摘要和更新时间。
- `edu_student_profile_version`：每次变更的版本、触发类型和摘要。
- `edu_student_profile_evidence`：支撑维度结论的原始证据。
- `edu_student_profile_message`：画像对话消息。

数据库结构由 `V61__add_student_profiles.sql` 创建。
