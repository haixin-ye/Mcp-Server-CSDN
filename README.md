# CSDN MCP Server

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-green)
![Spring AI MCP](https://img.shields.io/badge/Spring%20AI-MCP-orange)
![Playwright](https://img.shields.io/badge/Playwright-Browser%20Automation-2EAD33)
![Docker Ready](https://img.shields.io/badge/Docker-Ready-2496ED)

一个面向 **CSDN 自动发帖场景** 的 MCP Server。  
它为 Agent 或其他 MCP Client 提供统一的 `publishArticle` 工具入口，让调用方通过标准 MCP 协议完成 **登录引导、正文写入、草稿保存与状态返回**，而不需要自己处理 Cookie、动态签名头或浏览器自动化细节。

## 一眼看懂

- **对外只暴露一个核心工具**：`publishArticle`
- **首次未登录时自动返回登录链接**
- **登录态由服务端维护**
- **发帖在真实 CSDN Markdown 编辑器页面内完成**
- **支持本地运行与 Docker Desktop 部署**
- **支持 SSE 模式接入 MCP**

## 技术栈

这个项目当前主要基于以下技术实现：

- **Java 17**
- **Spring Boot 3.4**
- **Spring AI MCP Server**
- **Playwright for Java**
- **SSE（Server-Sent Events）**
- **Docker / Docker Compose**

`Playwright` 是这个项目的关键实现基础之一。  
它负责进入已登录的 CSDN 编辑器页面，在浏览器上下文中完成标题写入、Markdown 正文写入和保存草稿动作，从而避免手工维护动态鉴权头。

## 它解决了什么问题

如果直接做 CSDN 发帖接口调用，通常会遇到这些问题：

- Cookie 会过期
- 动态签名头不稳定
- 发帖请求难以长期复用
- 调用方不得不自己处理网页登录和鉴权细节

这个服务的目标就是把这些复杂度收进服务端，调用方只需要：

1. 连接 MCP 服务
2. 调用 `publishArticle`
3. 按需完成一次网页登录
4. 后续继续调用同一个工具

## 工作方式

这不是一个“固定 Cookie 反复调接口”的服务，而是一个“**登录一次，后续由服务维护登录态**”的服务。

整体流程如下：

```text
Agent 调用 publishArticle
        ↓
若未登录，服务返回 AUTH_REQUIRED + loginUrl
        ↓
用户在浏览器打开 loginUrl 并扫码登录 CSDN
        ↓
服务保存登录态
        ↓
Agent 再次调用 publishArticle
        ↓
服务进入 CSDN Markdown 编辑器页面
        ↓
自动写入标题、Markdown 正文并保存草稿
```

## 返回结果设计

为了方便 Agent 正确处理不同状态，服务会返回结构化结果，而不是只给一个简单字符串。

常见返回如下：

| `status` | `reason` | 含义 |
|---|---|---|
| `SUCCESS` | `PUBLISHED` | 发帖或保存草稿成功 |
| `AUTH_REQUIRED` | `LOGIN_REQUIRED` / `SESSION_EXPIRED` | 当前未登录或登录态失效，需要重新登录 |
| `FAILED` | `RATE_LIMITED` | CSDN 限制了短时间重复发帖 |
| `FAILED` | `CONTENT_WRITE_FAILED` | 已进入编辑器，但正文未成功写入 |
| `FAILED` | `PUBLISH_FAILED` | 普通业务失败 |

服务还会返回这些辅助字段：

- `message`
- `humanMessage`
- `loginPath`
- `loginUrl`
- `nextAction`
- `retryable`

## 快速开始

### 环境要求

- Java 17
- Maven 3.9+
- Docker Desktop（可选）

### 本地运行

先打包：

```bash
mvn -q -DskipTests package
```

再启动：

```bash
mvn spring-boot:run
```

默认地址：

```text
http://127.0.0.1:18080
```

## Docker 部署

### 1. 打包 Jar

```bash
mvn -q -DskipTests package
```

### 2. 构建镜像

```bash
docker compose build --pull=false
```

### 3. 启动容器

```bash
docker compose up -d
```

### 4. 查看日志

```bash
docker compose logs -f
```

默认端口映射：

```text
18080:18080
```

## MCP 接入

当前服务通过 **SSE** 提供 MCP 能力，接入配置示例：

```json
{
  "baseUri": "http://127.0.0.1:18080",
  "sseEndpoint": "/sse",
  "messageEndpoint": "/mcp/message"
}
```
请将127.0.0.1:18080替换为服务运行的地址以及端口号


## 如何使用这个 MCP 服务

### 首次使用

1. 将 Agent 或 MCP Client 连接到本服务
2. 调用 `publishArticle`
3. 若当前未登录，服务返回：
   - `status = AUTH_REQUIRED`
   - `loginPath`
   - `loginUrl`
   - `nextAction = OPEN_LOGIN_URL`
4. 在浏览器打开 `loginUrl`
5. 扫码登录 CSDN
6. 登录成功后，再次调用 `publishArticle`

### 发帖请求示例

```json
{
  "title": "Redis 多路复用技术详解",
  "markdowncontent": "# Redis 多路复用技术详解\n\n## 引言\n这里是正文内容。",
  "tags": "Redis,网络编程,数据库",
  "description": "介绍 Redis 多路复用模型及其实现原理。"
}
```

### 成功返回示例

```json
{
  "status": "SUCCESS",
  "reason": "PUBLISHED",
  "articleId": 123456,
  "articleUrl": "https://blog.csdn.net/..."
}
```

### 未登录返回示例

```json
{
  "status": "AUTH_REQUIRED",
  "reason": "LOGIN_REQUIRED",
  "loginUrl": "http://127.0.0.1:18080/auth/csdn/login?session=default",
  "nextAction": "OPEN_LOGIN_URL",
  "retryable": true
}
```

## 默认登录页

也可以直接访问默认登录页：

```text
http://127.0.0.1:18080/auth/csdn/login?session=default
```

## 项目结构

```text
src/main/java/cn/bugstack/mcp/server/csdn
├── domain          # 领域模型、服务、端口
├── infrastructure  # 网关适配、浏览器发帖实现、SSE transport
├── interfaces      # HTTP 接口与调试入口
└── types           # 配置与通用工具
```

## 常见问题

### 为什么第一次调用会返回登录链接？

因为当前没有可用登录态。首次使用必须先完成一次网页登录授权。

### 为什么服务重启后通常还能继续使用？

因为登录态会保存在服务本地数据目录中。只要当前实例数据还在，就可以继续复用。

### 为什么删除容器后需要重新登录？

当前设计就是“登录态保留在当前实例空间内”。删除容器后，实例内数据会一起消失。

### 为什么会返回 `CONTENT_WRITE_FAILED`？

这表示服务已经进入 CSDN 编辑器，但正文没有成功写入真实编辑器节点。通常属于页面结构变化或编辑器行为变化，需要重新适配自动化写入逻辑。

## Contributing

欢迎继续扩展，例如：

- 多账号支持
- 更完整的文章参数映射
- 更稳定的 CSDN 编辑器适配
- 更多 MCP Client 接入示例



## Roadmap

- 持续优化浏览器发帖链路稳定性
- 补充更多 Docker 与部署说明
- 增加更多 Agent / MCP Client 接入示例

