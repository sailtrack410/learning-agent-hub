# 第 1 章 Servlet 与 Web 基础

关联知识点：`HTTP`、`SERVLET`

## 学习目标

- 解释请求方法、状态码、请求头与响应体的作用。
- 描述 Servlet 初始化、请求处理和销毁三个阶段。
- 根据接口语义选择正确的 HTTP 方法和状态码。

## 核心讲义

HTTP 请求由方法、目标地址、请求头和可选请求体组成。`GET` 用于读取资源，`POST` 常用于创建资源，`PUT` 表达整体替换或幂等更新，`DELETE` 用于删除资源。响应状态码按类别表达结果：`2xx` 成功、`4xx` 客户端问题、`5xx` 服务端问题。

Servlet 由 Web 容器管理。容器创建实例后调用 `init()` 完成一次性初始化；每次请求进入 `service()`，再分派到 `doGet()`、`doPost()` 等方法；应用停止或实例移除前调用 `destroy()`。Servlet 通常被多个线程共享，因此不应把单次请求数据保存在实例字段中。

## 练习

1. 查询课程详情应使用什么方法？成功时返回什么状态码？
2. 创建课程时缺少必填名称，适合返回 `400` 还是 `500`？说明原因。
3. 为什么不能把当前登录用户保存在 Servlet 实例字段中？

## 参考答案

`GET + 200`；输入不合法属于客户端请求问题，应返回 `400`；Servlet 实例可能被并发请求共享，实例字段会造成用户数据串扰。

## 代码案例

```java
@WebServlet("/api/ping")
public class PingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"pong\"}");
    }
}
```

尝试补充：当查询参数 `name` 为空时返回 `400` 和可理解的 JSON 错误消息。
