# 《Java Web 应用开发》初始课程资料集

本目录是软件杯演示课程 `JAVA_WEB` 的首套可提交资料，内容与 `V52__add_education_course_foundation.sql` 中的 7 个章节、14 个知识点保持一致。

## 资料结构

- `manifest.csv`：课程、章节、知识点、资料文件和来源的关联清单。
- `chapters/01-07-*.md`：每章一份讲义，包含学习目标、核心讲解、一组练习、参考答案和一个可运行或可补全的代码案例。
- `sources.md`：资料编写所依据的规范和官方文档。
- `student-profiles.json`：基础、中等、进阶三类学生的 8 维画像样本。
- `common-errors.json`：常见错误、原因和纠正资源建议。
- `fact-check-cases.json`：不少于 20 条带官方来源的课程事实校验样本。
- `safety-cases.json`：不少于 10 条提示词注入、隐私、越权和危险操作测试样本。

## 使用方式

教师可在“课程管理 → Java Web 应用开发 → 课程资料库”中逐章上传 Markdown 文件，并按 `manifest.csv` 选择对应章节和知识点。系统会异步解析、分块和向量化，原始文件关联会保留在课程资料记录中。

演示账号、课程、章节和知识点由 Flyway 迁移 `V52`、`V55` 初始化，可重复部署到新数据库。

`EducationCourseDatasetTest` 会在后端全量测试中校验样本数量、唯一性、画像维度和事实来源，防止交付时出现缺文件或空数据。
