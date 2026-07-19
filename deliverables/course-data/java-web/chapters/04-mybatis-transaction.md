# 第 4 章 MyBatis 数据访问

关联知识点：`MYBATIS`、`TRANSACTION`

## 学习目标

- 编写参数化 SQL 和清晰的结果映射。
- 识别多表写入所需的事务边界。
- 通过唯一约束和条件更新实现幂等与并发控制。

## 核心讲义

MyBatis 负责 SQL 执行和结果映射。动态值必须使用参数绑定，不能把用户输入拼接到 SQL。Repository 只承担数据访问，不承载权限判断和完整业务流程。

事务应包围必须同时成功或失败的数据库写入。外部 HTTP、模型和文件转换不应放入长事务。幂等约束应尽量落在数据库唯一键上；进度和掌握度等并发更新可使用版本号或条件更新。

## 练习

1. 为什么 `${keyword}` 用于用户搜索存在风险？
2. 创建订单和订单明细为什么需要同一事务？
3. 仅在前端禁用重复提交按钮是否足够？

## 参考答案

可能造成 SQL 注入；必须保持聚合数据一致；不足，网络重试和并发请求仍可能重复写入。

## 代码案例

```java
@Transactional(rollbackFor = Exception.class)
public void enroll(Long courseId, Long studentId, String idempotencyKey) {
    if (repository.existsEnrollmentRequest(idempotencyKey)) {
        return;
    }
    repository.insertEnrollment(courseId, studentId, idempotencyKey);
    repository.initializeProgress(courseId, studentId);
}
```

数据库应为 `idempotency_key` 建立唯一约束，并测试并发重复请求。
