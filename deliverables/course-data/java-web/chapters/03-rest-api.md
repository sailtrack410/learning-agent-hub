# 第 3 章 REST API 设计

关联知识点：`REST`、`VALIDATION`

## 学习目标

- 使用资源名词设计一致的 URI。
- 区分协议校验、业务校验和权限校验。
- 设计统一、可追踪且不泄露内部细节的错误响应。

## 核心讲义

REST 接口以资源为中心，例如 `/api/courses/{courseId}/chapters`。方法表达动作，URI 不需要出现 `getCourse`、`deleteCourse` 等动词。接口应保持状态码、字段命名、分页和错误格式一致。

参数格式和长度可使用 Bean Validation 在入口校验；跨字段或状态相关规则由 Service 校验；数据范围与横向越权必须由后端权限逻辑处理。错误响应应包含稳定错误码、用户可理解消息和请求编号，但不能返回 SQL 或堆栈。

## 练习

1. 将 `/api/createChapter` 改为资源式接口。
2. 教师访问其他教师课程时为什么不能只依赖前端隐藏按钮？
3. 请求体名称为空与课程已发布不可修改分别属于哪类校验？

## 参考答案

`POST /api/courses/{id}/chapters`；前端可被绕过；前者是参数校验，后者是业务状态校验。

## 代码案例

```java
public class CourseSaveRequest {
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 128, message = "课程名称不能超过128个字符")
    private String courseName;
}

@PostMapping("/courses")
public R<CourseResponse> create(@Valid @RequestBody CourseSaveRequest request) {
    return R.ok(courseService.createCourse(request));
}
```

尝试补充：统一处理校验异常并返回字段级错误信息。
