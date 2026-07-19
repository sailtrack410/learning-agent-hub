# 第 6 章 自动化测试

关联知识点：`UNIT_TEST`、`INTEGRATION_TEST`

## 学习目标

- 使用单元测试隔离验证业务规则。
- 使用集成测试验证接口、数据库迁移与安全链路。
- 为缺陷编写能复现根因的回归测试。

## 核心讲义

单元测试关注一个业务责任，通过 Mock 隔离数据库或外部服务；集成测试关注组件组合、真实 SQL、事务、序列化和安全过滤器。二者用途不同，不能互相替代。

测试至少覆盖成功、边界、权限、重复提交、状态冲突和依赖失败。缺陷测试应先稳定复现错误场景，再证明修复后的预期行为，并检查相邻流程没有回归。

## 练习

1. Service 的课程权限判断适合优先写哪类测试？
2. Flyway 从零迁移应使用什么数据库？
3. 修复重复入库后应覆盖哪些并发或幂等场景？

## 参考答案

单元测试加接口集成测试；全新独立临时数据库；首次提交、重复提交、并发提交和失败重试。

## 代码案例

```java
@Test
void shouldRejectTeacherOutsideCourse() {
    doThrow(new BizException(404, "课程不存在或无权管理"))
        .when(accessService).requireTeacherCourse(2002L, 8100001L);

    assertThrows(BizException.class,
        () -> service.updateTeacherCourse(8100001L, request));

    verify(repository, never()).updateCourse(anyLong(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyLong());
}
```

尝试补充：未登录和学生角色访问教师接口的 MockMvc 测试。
