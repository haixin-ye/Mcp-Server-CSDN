# CSDN MCP 单工具登录态发布服务设计

## 1. 设计目标
将当前项目重构为一个可打包为 Docker 镜像的 CSDN MCP 服务。该服务对外只暴露一个发帖工具 `publishArticle`，内部自行完成登录态管理、首次登录引导、登录态恢复、失效识别和重新登录引导。

最终使用方式应尽量接近“下载镜像并运行即可”：
- 用户无需手工配置 cookie、nonce、signature
- 用户无需手工创建目录或准备配置文件
- 服务首次启动时自动完成目录和状态初始化
- 首次未登录时，引导用户通过浏览器登录一次
- 后续 agent 只提交文章内容即可发帖

## 2. 适用范围
本设计面向单个 CSDN 账号与单个 MCP 服务实例，不支持多用户隔离，也不要求账号切换。

服务运行在用户自己的本地 Docker 容器中，由 agent 通过 SSE 方式连接。登录态只要求保存在当前容器实例内部：
- 容器重启后，如果容器实例仍在，登录态应可继续使用
- 容器被删除后，登录态丢失是符合预期的行为

## 3. 非目标
- 多账号、多用户隔离
- 向 agent 暴露登录、状态管理等多个 MCP 工具
- 让调用者手工维护鉴权配置
- 在第一阶段实现浏览器页面自动发帖兜底

## 4. MCP 对外契约
对外只暴露一个 MCP 工具：

- `publishArticle(title, markdowncontent, tags, description)`

agent 不应感知 cookie、浏览器状态、会话文件、鉴权参数等实现细节。

## 5. 返回模型
`publishArticle` 必须返回结构化结果，不能返回 `null`。建议返回：

- `status`：`SUCCESS`、`AUTH_REQUIRED`、`FAILED`
- `message`：简洁说明
- `articleUrl`：发帖成功时返回
- `articleId`：发帖成功时返回
- `loginUrl`：需要登录或重新登录时返回
- `retryable`：是否适合重试

返回语义：
- `SUCCESS`：文章已成功发出
- `AUTH_REQUIRED`：当前未登录或登录态已失效，需用户完成一次网页登录
- `FAILED`：非登录态问题导致的失败

## 6. 总体架构
### 6.1 PublishTool
唯一的 MCP 工具入口。负责参数校验、调用会话管理器、执行发布流程，并将内部异常映射为统一返回模型。

### 6.2 SessionManager
负责读取会话状态、判断当前登录态是否存在且可用、在鉴权失败时更新状态，并决定是否需要进入登录流程。

### 6.3 LoginCoordinator
负责创建一次性登录会话、生成内部登录链接、协调浏览器登录完成后的状态写回。

### 6.4 SessionStore
负责将登录态和元数据写入容器内固定目录，供容器重启后恢复使用。

### 6.5 CsdnPublisher
真正执行 CSDN 发帖。第一阶段采用“已登录浏览器态支撑下的 API 发帖”。

## 7. 容器内零配置初始化
服务必须支持零配置启动。调用者不需要手工指定存储路径，也不需要额外准备目录。

容器内固定数据目录建议为：

- `/app/data/session/storage-state.json`
- `/app/data/session/session-meta.json`

服务启动时必须自动完成：
1. 检查 `/app/data/session` 是否存在，不存在则创建
2. 检查 `session-meta.json` 是否存在，不存在则自动初始化
3. 检查 `storage-state.json` 是否存在，不存在则标记为未登录
4. 启动登录辅助 HTTP 路由
5. 使 SSE MCP 服务进入可接入状态

调用者不需要手工配置数据路径。路径作为服务内部约定，而不是外部必填配置。

## 8. 会话状态模型
固定使用以下状态：

- `UNBOUND`：从未完成登录，或没有可用登录态文件
- `LOGIN_PENDING`：已返回登录链接，等待用户完成网页登录
- `ACTIVE`：存在已保存登录态，且最近校验可用
- `EXPIRED`：检测到登录态失效，必须重新登录

`session-meta.json` 建议至少包含：

- `state`
- `updatedAt`
- `lastValidatedAt`
- `lastError`
- `pendingLoginSessionId`
- `pendingLoginUrl`

## 9. 健壮性要求：会话失效识别
服务不能等到用户看到莫名报错才知道登录态失效，必须具备明确的识别与降级行为。

至少要覆盖以下情况：
- 本地不存在 `storage-state.json`
- 会话文件存在但无法解析
- 发帖接口返回明确未授权或鉴权失败
- 页面或接口返回“需要重新登录”语义
- 依赖的登录态字段缺失，导致无法构造有效请求

处理原则：
1. 一旦判断为登录态问题，不返回模糊失败
2. 应立即将状态更新为 `EXPIRED` 或 `UNBOUND`
3. 对外返回 `AUTH_REQUIRED`
4. 返回新的或现有的 `loginUrl`
5. 将错误摘要写入 `session-meta.json`

也就是说，登录态掉了时，系统应“识别并引导重登”，而不是“报错但不知道为何失败”。

## 10. 发帖流程
1. agent 调用 `publishArticle`
2. `PublishTool` 校验文章参数
3. `SessionManager` 读取会话元数据和登录态文件
4. 如果状态为 `UNBOUND`，生成登录链接并返回 `AUTH_REQUIRED`
5. 如果状态为 `LOGIN_PENDING`，直接返回已有登录链接和 `AUTH_REQUIRED`
6. 如果状态为 `ACTIVE`，调用 `CsdnPublisher` 发帖
7. 如果发帖成功，返回 `SUCCESS`
8. 如果识别为登录态失效，更新状态为 `EXPIRED`，并返回 `AUTH_REQUIRED`
9. 如果为普通业务失败或上游异常，返回 `FAILED`

## 11. 登录流程
登录流程不是 MCP 工具，而是服务内部 HTTP 辅助能力。

流程如下：
1. `publishArticle` 在未登录或登录态失效时触发登录引导
2. `LoginCoordinator` 生成 `loginSessionId`
3. 服务返回类似 `/auth/csdn/login?session=<id>` 的登录链接
4. 用户在浏览器打开该链接并完成 CSDN 登录
5. Playwright 捕获登录后的浏览器状态并写入 `storage-state.json`
6. `session-meta.json` 更新为 `ACTIVE`
7. agent 再次调用同一个 `publishArticle` 时，直接尝试发帖

## 12. 配置原则
对最终调用者采用零配置原则，以下内容不应要求手工填写：
- 数据目录路径
- cookie、nonce、signature
- 会话文件初始化内容

可以保留少量内部默认配置，例如：
- 服务端口
- 登录会话超时时间
- 容器内数据目录常量

但这些应具备默认值，不能成为首次运行前提。

## 13. 错误处理
- 不允许返回 `null`
- 登录态相关问题统一返回 `AUTH_REQUIRED`
- 参数校验失败返回 `FAILED`
- 上游临时异常返回 `FAILED`，并可设置 `retryable=true`
- 所有内部错误都应转为可理解的 `message`
- 最近一次关键错误写入 `session-meta.json`

## 14. 测试策略
第一阶段至少覆盖以下测试：

- 首次启动自动初始化目录和会话文件
- `UNBOUND -> LOGIN_PENDING -> ACTIVE -> EXPIRED` 状态流转
- 登录态文件缺失时返回 `AUTH_REQUIRED`
- 登录态文件损坏时返回 `AUTH_REQUIRED`
- 发帖成功返回 `SUCCESS`
- 鉴权失败时自动转为 `AUTH_REQUIRED`
- 普通失败时返回 `FAILED`
- 容器重启后的会话恢复逻辑

## 15. 分阶段实现计划
### 第一阶段
完成以下能力：
- 单一 `publishArticle` 工具
- 容器内固定目录自动初始化
- 登录态文件与元数据文件持久化
- 浏览器登录引导
- 登录态失效识别
- 基于已登录态的 API 发帖

### 第二阶段
如果 CSDN API 发布在登录态恢复后仍然脆弱，再增加浏览器页面自动发帖兜底能力。
