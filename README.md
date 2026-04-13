# CSDN MCP Server

一个面向 CSDN 发帖场景的 MCP Server，让 Agent 或其他 MCP Client 通过标准 MCP 协议完成草稿保存与发帖，而不需要手工维护 Cookie、签名头或浏览器自动化细节。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-green)
![MCP SSE](https://img.shields.io/badge/MCP-SSE-orange)
![Docker Ready](https://img.shields.io/badge/Docker-Ready-2496ED)

## 目录

- [项目简介](#项目简介)
- [核心特性](#核心特性)
- [工作方式](#工作方式)
- [快速开始](#快速开始)
- [Docker Desktop 部署](#docker-desktop-部署)
- [MCP 接入](#mcp-接入)
- [首次使用流程](#首次使用流程)
- [常见问题](#常见问题)
- [项目结构](#项目结构)
- [Contributing](#contributing)
- [Roadmap](#roadmap)
- [License](#license)

## 项目简介

本项目基于 Java 17、Spring Boot、Spring AI MCP Server 与 Playwright 实现，适合运行在本地开发机、Docker Desktop 或自托管环境中。

它解决的是这样一个问题：

- 调用方希望通过 MCP 让 Agent 自动发 CSDN 文章
- 但不希望自己处理 Cookie、动态鉴权头和网页发帖流程
- 同时又希望首次登录后，后续尽量自动化完成发帖

本服务的核心目标是：

- 提供统一的 CSDN 发帖 MCP 能力
- 首次使用时通过网页登录完成授权
- 登录成功后由服务自身维护登录态
- 后续通过浏览器上下文完成草稿保存与发帖动作

## 核心特性

- 单实例、单账号登录态维护
- 首次未登录时自动返回登录链接
- 登录失效自动识别并要求重新登录
- 使用浏览器上下文执行保存草稿，避免手工维护动态签名
- 支持本地运行与 Docker 部署
- 支持 SSE 模式 MCP 接入

## 工作方式

这不是一个“缓存固定 Cookie 后反复调接口”的服务，而是一个“登录一次，后续由服务维护”的服务。

整体流程如下：

1. 首次调用发帖工具时，如果没有可用登录态，服务返回登录链接。
2. 用户打开登录链接，在内置页面中扫码登录 CSDN。
3. 服务保存当前登录状态，并在后续请求中复用。
4. 发帖时，服务进入 CSDN Markdown 编辑页，在已登录的浏览器上下文中完成标题、正文填充与保存草稿动作。
5. 如果登录态失效，服务会自动识别并再次要求登录。

这种实现方式的直接价值是：调用方不需要自己处理 `cookie`、`x-ca-nonce`、`x-ca-signature` 等动态鉴权参数。

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

## Docker Desktop 部署

### 构建镜像

```bash
docker compose build
```

### 启动容器

```bash
docker compose up -d
```

### 查看日志

```bash
docker compose logs -f
```

### 说明

- Docker Compose 文件位于根目录：[docker-compose.yml](./docker-compose.yml)
- 默认端口映射为 `18080:18080`
- 删除容器后，容器内部登录态会一起丢失

## MCP 接入

当前服务以 SSE 方式对外提供 MCP 能力，典型配置如下：

```json
{
  "baseUri": "http://127.0.0.1:18080",
  "sseEndpoint": "/sse",
  "messageEndpoint": "/mcp/message"
}
```

如果调用方运行在 Docker 容器内部，请将 `127.0.0.1` 替换为实际可访问该服务的宿主机地址或容器地址。

## 首次使用流程

```text
调用 publishArticle
        ↓
返回 AUTH_REQUIRED + loginUrl
        ↓
打开登录页并扫码登录 CSDN
        ↓
服务保存登录态
        ↓
再次调用 publishArticle
        ↓
服务进入编辑器页面并保存草稿 / 发帖
```

也可以直接访问登录页：

```text
http://127.0.0.1:18080/auth/csdn/login?session=default
```

## 常见问题

### 为什么第一次调用会返回登录链接？

因为当前没有可用登录态。首次使用必须先完成一次网页登录授权。

### 为什么服务重启后通常还能继续使用？

因为登录态会保存在服务本地数据目录中。只要当前实例的数据还在，就可以继续复用。

### 为什么删除容器后需要重新登录？

因为当前设计就是“登录态保留在当前实例空间内”。删除容器等于删除实例数据。

### 为什么 `/sse` 返回 404？

通常是镜像没有使用最新构建结果，或者没有重新打包并重建容器。更新依赖后需要重新执行：

```bash
mvn -q -DskipTests package
docker compose build
docker compose up -d
```

## 项目结构

```text
src/main/java/cn/bugstack/mcp/server/csdn
├── domain          # 领域模型、服务、端口
├── infrastructure  # 网关适配、浏览器发帖实现、持久化
├── interfaces      # HTTP 接口与调试入口
└── types           # 配置与通用工具
```

## Contributing

欢迎在此基础上继续扩展，例如：

- 多账号支持
- 更完整的发帖参数映射
- 更稳定的页面选择器与编辑器适配
- 更完整的 MCP Client 接入样例

提交代码前建议先执行：

```bash
mvn -q test
```

## Roadmap

- 支持更完整的文章参数配置
- 优化浏览器发帖链路的稳定性
- 补充更完整的 Docker 与部署说明
- 增加更多 MCP Client 接入示例

## License

当前仓库未单独提供许可证文件。如果你计划公开分发或开源发布，建议补充 `LICENSE` 文件后再对外发布。

## 说明

本项目当前以“单实例维护单账号登录态”为主要设计模式，更适合个人本地使用或单租户部署场景。
