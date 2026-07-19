# 第 2 章 Spring Boot 快速开发

关联知识点：`SPRING_IOC`、`SPRING_BOOT`

## 学习目标

- 理解 IoC 容器、依赖注入和组件扫描。
- 说明 Spring Boot 自动配置与条件装配的基本机制。
- 建立 Controller、Service、Repository 职责清晰的应用结构。

## 核心讲义

IoC 将对象创建和依赖组装交给容器。构造器注入能明确表达必需依赖，也便于单元测试。`@Component`、`@Service`、`@Repository` 和 `@Controller` 都可让组件进入容器，但应按真实职责选择注解。

Spring Boot 根据类路径、配置属性和已有 Bean 进行条件化自动配置。自动配置减少样板代码，但不会替代业务设计。配置应放在 `application.yml` 或环境变量中，敏感值不能写死在源码。

## 练习

1. 为什么优先使用构造器注入？
2. Controller 是否适合直接编写多表事务？
3. 自动配置未生效时应检查哪三类条件？

## 参考答案

依赖明确、对象可保持完整、易测试；事务属于 Service；检查依赖是否存在、配置条件、是否已有覆盖 Bean。

## 代码案例

```java
@Service
public class CourseQueryService {
    private final CourseRepository courseRepository;

    public CourseQueryService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public CourseResponse getPublishedCourse(Long courseId) {
        return courseRepository.findPublished(courseId)
            .orElseThrow(() -> new BizException(404, "课程不存在"));
    }
}
```

尝试补充：为 `courseId` 为空和课程不存在分别编写测试。
