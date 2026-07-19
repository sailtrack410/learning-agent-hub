# 第 5 章 认证与权限

关联知识点：`AUTHN`、`AUTHZ`

## 学习目标

- 区分身份认证、角色权限、操作权限和数据权限。
- 使用安全哈希保存密码并保护会话。
- 编写未登录、无权限和横向越权测试。

## 核心讲义

认证回答“你是谁”，授权回答“你能做什么”。角色只能作为权限集合入口，最终接口仍需校验操作权限和数据范围。例如教师角色可以管理课程，但只能管理本人创建或受邀协作的课程。

密码必须使用 BCrypt 等自带盐的慢哈希算法，Token 和会话标识不能写入日志。前端隐藏入口只改善体验，不能替代后端鉴权。对不存在或无权访问的数据可统一返回 `404`，减少资源枚举风险。

## 练习

1. 教师能编辑自己课程但不能编辑他人课程，涉及哪些权限层次？
2. 为什么不能使用普通 SHA-256 直接保存密码？
3. 至少列出三种课程接口安全测试。

## 参考答案

角色、操作和数据权限；普通哈希过快且缺少专门抗破解设计；未登录、学生角色访问、其他教师横向越权、课程所有者成功。

## 代码案例

```java
public CourseResponse requireTeacherCourse(Long userId, Long courseId) {
    requireEducationRole(userId, "TEACHER");
    CourseResponse course = repository.getTeacherCourse(courseId, userId);
    if (course == null) {
        throw new BizException(404, "课程不存在或无权管理");
    }
    return course;
}
```

尝试补充：协作教师可编辑但不能删除课程的测试。
