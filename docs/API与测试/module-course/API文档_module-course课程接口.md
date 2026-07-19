# module-course 课程接口

## 学生接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/employee/courses` | 查询已发布课程 |
| `GET` | `/api/employee/courses/{courseId}` | 查询已发布课程与章节 |
| `POST` | `/api/employee/courses/{courseId}/enrollment` | 幂等加入已发布课程 |
| `GET` | `/api/employee/courses/resources/{attachmentRefId}` | 打开有权访问的课程资源 |
| `GET` | `/api/employee/courses/resources/{attachmentRefId}/preview` | 读取已生成的 PDF 预览 |
| `GET` | `/api/employee/student/learning-dashboard` | 聚合本人今日任务、学习进度、掌握度、待完成练习和推荐资源 |

学生只能读取已发布课程、已发布章节及其封面、正文图片、视频和文件。课程列表与详情返回 `enrolled`，学生首次进入未加入课程时由前端调用加入接口；同一学生重复加入不会产生重复关系。

学习首页接口仅允许学生角色访问，教师访问返回 `403`。接口只读聚合活动学习路径、节点进度、知识点掌握度、已发布测验和推荐记录，不会在打开首页时创建路径、完成节点或修改画像。没有学习路径时进度与任务返回零值和空列表，由页面展示明确空状态。

## 教师接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/employee/teacher/courses` | 查询本人作为所有者或协作者管理的课程 |
| `GET` | `/api/employee/teacher/courses/{courseId}` | 查询本人参与管理的课程 |
| `POST` | `/api/employee/teacher/courses` | 创建课程，当前教师成为所有者 |
| `PUT` | `/api/employee/teacher/courses/{courseId}` | 所有者或协作者更新课程基础信息 |
| `PUT` | `/api/employee/teacher/courses/{courseId}/outline` | 所有者或协作者保存章节与知识点 |
| `PUT` | `/api/employee/teacher/courses/{courseId}/chapters/{chapterId}/article` | 所有者或协作者保存、发布章节文章 |
| `POST` | `/api/employee/teacher/courses/{courseId}/resources/{attachmentRefId}/preview` | 提交课程内 Word、PowerPoint 的异步 PDF 预览任务 |
| `GET` | `/api/employee/teacher/courses/{courseId}/resources/{attachmentRefId}/preview-status` | 查询附件预览的 `PENDING/CONVERTING/READY/FAILED` 状态 |
| `GET` | `/api/employee/teacher/courses/{courseId}/knowledge-documents` | 查询课程资料解析、向量化和入库状态 |
| `POST` | `/api/employee/teacher/courses/{courseId}/knowledge-documents` | 将课程资料关联章节、知识点后加入异步入库队列 |
| `POST` | `/api/employee/teacher/courses/{courseId}/knowledge-documents/{documentId}/retry` | 重试失败的课程资料入库任务 |
| `GET` | `/api/employee/teacher/courses/{courseId}/learning-overview` | 查询课程、班级、学生画像摘要、学习进度、风险学生、薄弱知识点和资源使用统计 |
| `POST` | `/api/employee/teacher/courses/{courseId}/students/{studentUserId}/private-conversation` | 与已加入当前课程的学生创建或进入幂等私聊 |
| `POST` | `/api/employee/teacher/courses/{courseId}/classes/{classId}/conversation` | 创建、同步或进入课程班级群 |
| `DELETE` | `/api/employee/teacher/courses/{courseId}` | 仅所有者删除课程 |
| `POST` | `/api/employee/teacher/courses/{courseId}/invitations` | 仅所有者生成教师邀请令牌 |
| `POST` | `/api/employee/teacher/courses/invitations/accept` | 教师接受邀请并成为协作者 |

邀请令牌 7 天内有效且仅可接受一次，数据库只保存 SHA-256 摘要。重复接受仅对原接受者返回已有课程，其他教师使用已接受令牌返回冲突。

## 数据表

- `edu_course_teacher`：课程与教师关系，角色为 `OWNER` 或 `COLLABORATOR`。
- `edu_course_enrollment`：课程与学生的有效加入关系，联合主键为 `(course_id, student_user_id)`。
- 章节正文统一为 `ARTICLE`，Tiptap JSON 中的图片、视频和附件节点使用 `attachmentRefId` 关联课程资源；后端逐项校验资源归属。
- `edu_course_invitation`：邀请摘要、邀请人、有效期、接受人和接受时间。
- `V57__add_course_teacher_collaboration.sql`：回填现有课程创建者为所有者。
- `V58__unify_course_chapter_articles.sql`：将原视频、文件章节迁移为文章内视频或附件节点。
- `sys_attachment_preview`：保存原附件、PDF 预览附件及转换状态；原文件继续用于下载。
- `V59__add_course_resource_pdf_previews.sql`：增加课程附件 PDF 预览关系。
- `edu_course_knowledge_space`：课程与独立知识库空间的一对一关系。
- `edu_course_knowledge_document`：保存课程、原始附件、知识库文档和章节来源。
- `edu_course_knowledge_document_point`：保存资料关联的知识点来源。
- `V60__add_course_knowledge_documents.sql`：增加课程资料知识库关联。
- `V67__add_knowledge_ingest_progress.sql`：为知识入库任务增加进度百分比和当前阶段。
- `V72__add_course_enrollment.sql`：增加课程学生关系，并把已有课程学习活动回填为已加入状态。

Office 预览支持 `DOC/DOCX/PPT/PPTX`，单文件默认不超过 100 MB，单次 LibreOffice 转换默认最多执行 120 秒。`POST` 只创建或复用转换任务并立即返回，前端通过状态接口轮询；`READY` 后使用学生资源预览接口加载 PDF，`FAILED` 返回可理解的失败原因。PDF 直接使用原文件预览。正文只保存附件引用和 `previewAvailable`，预览 URL 由后端根据 `attachmentRefId` 生成，不能由客户端指定外部 iframe 地址。

课程资料入库复用现有 RAG 解析、分块、向量化和知识入库线程池。接口提交后立即返回 `PENDING`，教师端轮询 `PENDING/RUNNING/SUCCESS/FAILED` 状态、`0-100` 进度和当前处理阶段；失败时展示原因并允许重试。重复提交同一课程附件返回原文档，不重复创建入库任务。

学情概览只允许课程所有者或协作教师读取，学生范围严格来自 `edu_course_enrollment`，不再把平台全部学生视为课程学生。班级按学生 `primaryDeptId` 分组；未分班学生计入课程总览和学生明细，但不创建班级群。风险由画像完整度、学习路径进度、平均掌握度和测验完成情况共同判断。

课程班级群使用 `COURSE_CLASS:{courseId}:{classId}` 作为会话唯一键，重复进入返回同一个群并同步当前课程教师与该班已加入学生。课程私聊入口先校验教师课程成员关系和学生课程加入关系，再复用 IM 私聊唯一键；非课程教师、未加入课程的学生和跨课程目标均被后端拒绝。

教师定向推送个性化资源时同样复用课程加入关系：学生目标必须已加入资源所属课程，班级目标至少包含一名已加入学生；收到班级推送但未加入课程的同班学生不能读取资源。

学生端“学习资料”和教师端“教学资源”复用课程详情、个性化资源和课程知识资料接口，按课程、章节与资源类型统一组织。教师查看课程入库资料时，“查看原文件”使用真实课程资源链接；后端继续按课程参与关系校验，不能仅依赖前端隐藏入口。
