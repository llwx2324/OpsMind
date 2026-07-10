# OpsMind 生产级改造评估报告

生成日期：2026-07-06  
评估范围：当前仓库 `OpsMind` 的 Spring Boot Agent、AIOps、RAG、Milvus、文件上传和基础工程配置。

## 1. 结论摘要

当前项目更接近一个可运行、可演示的 Agent/RAG/AIOps Demo 或 PoC，而不是生产级 OnCall Agent 平台。

项目已经具备生产级方案的雏形：

- 有 Spring Boot API 服务。
- 有 ReactAgent 对话链路。
- 有 Planner/Executor/Supervisor 多 Agent 编排。
- 有 Milvus 向量库、文档上传、切片、Embedding、检索链路。
- 有 Prometheus 告警查询工具的真实 API 调用雏形。
- 有 SSE 流式输出和基础 Web 页面。

但目前生产化短板也比较明确：

- 真实运维数据接入不足，CLS 日志真实查询尚未实现。
- Prometheus 和 CLS 默认启用 Mock，AIOps 结果更偏演示闭环。
- 会话、任务、工具调用和报告结果没有持久化。
- 安全、权限、审计、限流、资源隔离基本缺失。
- Agent 输出缺少强制证据链、结构化校验和可追溯性。
- 工程化运维能力不足，缺少 Actuator、业务指标、部署探针、生产 profile、CI/CD。
- 测试覆盖很薄，目前仅有少量单元测试。

因此，建议将当前项目定位为“生产级 OnCall Agent 的原型工程”，后续按真实数据接入、可靠性、安全治理、可观测性、评测体系、部署运维六条主线推进。

## 2. 当前 Demo 特征证据

### 2.1 默认 Mock 和外部工具未完全启用

配置文件中 Prometheus 和 CLS 默认开启 Mock：

- `src/main/resources/application.yml:75`：`prometheus.mock-enabled: true`
- `src/main/resources/application.yml:79`：`cls.mock-enabled: true`

MCP 客户端默认关闭：

- `src/main/resources/application.yml:42`：`spring.ai.mcp.client.enabled: false`

CLS 真实查询尚未实现：

- `src/main/java/org/example/agent/tool/QueryLogsTools.java:181`：真实模式直接返回“CLS 真实查询尚未实现，请启用 mock 模式进行测试”

这说明 AIOps 链路当前更多依赖模拟告警和模拟日志来跑通流程。

### 2.2 会话状态只在 JVM 内存中

会话存储在内存 Map 中：

- `src/main/java/org/example/controller/ChatController.java:50`：`ConcurrentHashMap`
- `src/main/java/org/example/controller/ChatController.java:394`：`sessions.computeIfAbsent(...)`

问题：

- 服务重启后会话丢失。
- 多实例部署时不同实例之间无法共享会话。
- 没有 TTL、容量上限、淘汰策略和持久化审计。
- 不适合生产环境的横向扩容。

### 2.3 SSE 使用无界线程池

当前控制器中使用：

- `src/main/java/org/example/controller/ChatController.java:47`：`Executors.newCachedThreadPool()`

问题：

- 高并发或慢请求下可能无限创建线程。
- 无队列长度、无拒绝策略、无背压。
- Agent 调用和 SSE 长连接容易拖垮服务实例。

### 2.4 HTTP 错误语义不清晰

部分失败被包装成 HTTP 200：

- `src/main/java/org/example/controller/ChatController.java:67`
- `src/main/java/org/example/controller/ChatController.java:104`
- `src/main/java/org/example/controller/ChatController.java:117`
- `src/main/java/org/example/controller/ChatController.java:130`

问题：

- API 网关、监控、调用方 SDK 很难根据 HTTP 状态码判断失败。
- 告警系统无法准确统计 4xx/5xx。
- 客户端必须解析业务字段才能知道请求是否失败。

### 2.5 文件上传链路偏原型

上传文件直接使用原始文件名落本地：

- `src/main/java/org/example/controller/FileUploadController.java:59`：`uploadDir.resolve(originalFilename).normalize()`
- `src/main/java/org/example/controller/FileUploadController.java:67`：`Files.copy(...)`
- `src/main/java/org/example/controller/FileUploadController.java:74`：上传后同步调用 `vectorIndexService.indexSingleFile(...)`

问题：

- 文件名需要更严格的路径穿越防护。
- 缺少文件大小限制、内容嗅探、病毒扫描、重复文件版本治理。
- 上传接口同步执行向量索引，容易出现长请求和超时。
- 索引失败时上传仍返回成功，状态语义不清晰。

### 2.6 RAG 链路缺少生产级质量治理

当前 RAG 可以完成上传、切片、Embedding、Milvus 检索，但缺少：

- 文档权限和租户隔离。
- 文档版本、来源、生命周期管理。
- 异步索引任务和失败重试。
- 检索重排、召回阈值、去重、引用约束。
- RAG 离线评测集。
- 命中率、召回率、答案引用准确率监控。

相关代码：

- `src/main/java/org/example/service/DocumentChunkService.java`
- `src/main/java/org/example/service/VectorIndexService.java`
- `src/main/java/org/example/service/VectorSearchService.java`
- `src/main/java/org/example/agent/tool/InternalDocsTools.java`

### 2.7 向量库配置需要参数化和验证

Milvus collection 和向量维度写在常量中：

- `src/main/java/org/example/constant/MilvusConstants.java:13`：collection 名称 `biz`
- `src/main/java/org/example/constant/MilvusConstants.java:18`：向量维度 `1024`
- `src/main/java/org/example/client/MilvusClientFactory.java:166`：索引类型 `IVF_FLAT`
- `src/main/java/org/example/client/MilvusClientFactory.java:167`：距离类型 `L2`

问题：

- Embedding 模型变化后，向量维度可能不匹配。
- collection 名、索引类型、metric type 不适合硬编码。
- 缺少 schema 版本和迁移策略。
- 缺少 collection/index 初始化幂等性和状态校验。

### 2.8 生产工程依赖不足

当前有 Web 和 Test starter：

- `pom.xml:84`：`spring-boot-starter-web`
- `pom.xml:150`：`spring-boot-starter-test`

但没有看到生产常用能力：

- `spring-boot-starter-actuator`
- `spring-boot-starter-security`
- Micrometer/Prometheus 指标导出配置
- Resilience4j 或等价熔断限流组件
- OpenTelemetry tracing
- 统一异常处理和请求日志中间件

同时生产包中包含 devtools：

- `pom.xml:88`：`spring-boot-devtools`

### 2.9 测试覆盖不足

当前执行 `mvn test` 通过，结果为：

- 测试数：3
- 失败：0
- 错误：0

但现有测试主要覆盖 `ChatService` 的少量配置和 prompt 文本，不足以支撑生产上线。缺少：

- Controller API 测试。
- Agent 工具调用测试。
- RAG 检索测试。
- Milvus 集成测试。
- 文件上传安全测试。
- Mock/真实数据源切换测试。
- Agent 输出格式校验测试。

## 3. 生产级目标状态

生产级 OpsMind 应该满足以下目标：

### 3.1 真实数据闭环

系统应能从真实告警、指标、日志、链路追踪、CMDB、发布变更和 Runbook 中收集证据，并形成可审计报告。

最低要求：

- Prometheus 查询真实告警和指标。
- 日志平台真实可查，并支持时间范围、服务名、实例、traceId、关键词等条件。
- 工具调用失败必须有明确错误码、重试策略和降级结果。
- Agent 不允许编造无法从工具返回中证明的数据。

### 3.2 可追溯 Agent 决策

每次 Agent 分析都应保存：

- 输入告警。
- 规划步骤。
- 每次工具调用名称、参数、耗时、状态、返回摘要。
- 关键证据引用。
- 最终结论。
- 人工确认或反馈。

### 3.3 可横向扩展

系统应支持多实例部署：

- 会话状态外置。
- Agent 任务状态外置。
- SSE 或异步任务可恢复或可查询。
- 文件和向量索引任务不绑定单个进程。

### 3.4 安全和治理

系统应具备：

- 登录认证。
- RBAC 权限。
- 多环境隔离。
- 敏感信息脱敏。
- 文件上传安全控制。
- 高风险动作审批。
- 全量审计日志。

### 3.5 可观测和可运营

系统应能回答：

- 当前有多少 Agent 任务在运行。
- 工具调用成功率是多少。
- LLM 请求耗时和失败率是多少。
- 每日 token 成本是多少。
- 哪些告警分析准确，哪些被人工纠正。
- 哪些 Runbook 命中率低。

## 4. 分阶段改造路线

## P0：从 Demo 到可接真实场景

目标：让系统能基于真实数据给出可审计的 OnCall 分析报告。

### 4.1 关闭默认 Mock，完善真实数据源

改造内容：

- 将生产 profile 中 `prometheus.mock-enabled` 和 `cls.mock-enabled` 固定为 `false`。
- 实现 CLS 或目标日志平台真实查询。
- 支持服务名、实例、namespace、时间范围、关键词、traceId 查询。
- 对 Prometheus 增加指标查询能力，不只查 alerts。
- 工具返回统一结构：`success`、`errorCode`、`message`、`query`、`data`、`evidenceRefs`。

验收标准：

- 给定真实告警 ID，可以查询对应告警、指标和日志。
- 工具失败时 Agent 最终报告明确写出失败原因，不生成虚假根因。
- 生产 profile 启动时如果仍开启 mock，应直接启动失败或打印高危告警。

### 4.2 引入任务和会话持久化

改造内容：

- 使用 PostgreSQL/MySQL 存储会话、任务、报告、工具调用记录。
- Redis 可用于短期会话缓存和 SSE 状态缓存。
- Agent 分析任务引入 `taskId`，接口先创建任务，再异步执行。
- 前端通过 `taskId` 查询状态或订阅 SSE。

验收标准：

- 服务重启后能查询历史报告。
- 多实例部署下会话一致。
- 每次分析都有完整执行轨迹。

### 4.3 替换无界线程池

改造内容：

- 使用 Spring `ThreadPoolTaskExecutor`。
- 配置 core size、max size、queue capacity、rejection policy。
- 对 LLM 调用、工具调用、SSE 连接分别设置并发上限。
- 增加请求取消和超时中断。

验收标准：

- 压测下线程数受控。
- 请求超限时返回明确 429 或业务错误。
- 单个慢任务不会拖垮服务。

### 4.4 统一错误处理和 HTTP 语义

改造内容：

- 增加 `@RestControllerAdvice`。
- 定义统一错误码。
- 参数错误返回 400。
- 未授权返回 401。
- 无权限返回 403。
- 资源不存在返回 404。
- 外部依赖失败返回 502/503。
- 内部异常返回 500。

验收标准：

- 客户端可以只通过 HTTP 状态码判断请求大类是否成功。
- 业务错误响应结构一致。
- 日志中有 requestId/traceId。

### 4.5 Agent 输出强校验

改造内容：

- Planner/Executor 输出 JSON Schema。
- 最终报告结构化生成，再渲染 Markdown。
- 每个结论必须绑定证据来源。
- 没有证据时输出“不确定”或“无法判断”，不能输出确定根因。

验收标准：

- Agent 输出不合规时自动重试或失败。
- 报告中每个根因结论都能追溯到工具返回。
- 测试中模拟空日志、查询失败、部分数据缺失时，Agent 不编造结果。

## P1：从可用到稳定可运维

目标：让系统具备生产长期运行的可靠性、可观测性和质量保障。

### 4.6 增加 Actuator 和业务指标

改造内容：

- 引入 `spring-boot-starter-actuator`。
- 暴露 health、readiness、liveness。
- 接入 Micrometer。
- 记录 Agent 任务数、工具调用成功率、LLM 耗时、RAG 检索耗时、SSE 连接数。

验收标准：

- Kubernetes 可以使用探针判断服务状态。
- Prometheus 能采集业务指标。
- 可以针对外部工具失败率配置告警。

### 4.7 安全认证和权限模型

改造内容：

- 引入 Spring Security。
- 支持 OIDC、JWT 或企业 SSO。
- 定义角色：普通用户、OnCall、SRE 管理员、平台管理员。
- 工具按权限开放，例如日志查询、生产环境查询、修复建议生成。

验收标准：

- 未登录不能访问 API。
- 非授权用户不能查询生产日志。
- 审计日志记录用户、接口、工具、参数摘要和结果。

### 4.8 文件上传和知识库治理

改造内容：

- 文件上传改为对象存储或受控文件服务。
- 文件名随机化，保留原始文件名作为 metadata。
- 限制文件大小、MIME、扩展名和内容类型。
- 引入文档版本、归属团队、环境、权限标签。
- 索引任务异步化。

验收标准：

- 上传大文件不会阻塞 API 请求。
- 索引失败可重试。
- 用户只能检索有权限的知识文档。

### 4.9 RAG 质量提升

改造内容：

- 增加检索阈值。
- 增加 reranker。
- 增加同源 chunk 去重。
- 返回引用片段、来源文件、版本号。
- 建立 RAG 离线评测集。

验收标准：

- 能统计每批文档的召回质量。
- 答案必须带引用。
- 低置信度检索不会强行生成确定答案。

### 4.10 CI/CD 和部署标准化

改造内容：

- 增加 Dockerfile。
- 增加 Helm chart 或 K8s manifests。
- 增加 GitHub Actions/GitLab CI。
- 增加单元测试、集成测试、镜像构建、安全扫描。
- 配置 dev/test/prod profile。

验收标准：

- 每次提交自动运行测试。
- 每次合并自动构建镜像。
- 生产配置不允许默认 key、devtools、mock。

## P2：从工具到 OnCall 平台

目标：从“辅助分析”升级为“人机协同的 OnCall 工作台”。

### 4.11 告警生命周期管理

改造内容：

- 对接告警平台，支持告警确认、静默、升级、恢复。
- 对接工单系统，自动创建/更新事故工单。
- 对接 IM，推送分析结果和待确认动作。

验收标准：

- 告警报告可以关联工单。
- OnCall 可以在报告中看到状态流转。
- 关键动作有人工确认。

### 4.12 Runbook 和自动化执行

改造内容：

- 将内部文档升级为结构化 Runbook。
- Runbook 包含适用条件、检查步骤、命令模板、风险等级、回滚方式。
- 高风险命令必须审批。
- 自动化动作先做 dry-run，再执行。

验收标准：

- Agent 可以推荐 Runbook。
- Agent 不能绕过审批直接执行高危动作。
- 每次执行都有审计记录和回滚建议。

### 4.13 Agent 评测体系

改造内容：

- 建立历史事故样本集。
- 评估根因准确率、证据引用率、工具调用成功率、幻觉率、处理建议可执行性。
- 每次 prompt、模型或工具变更后自动回归。

验收标准：

- 关键版本上线前有评测报告。
- 评测失败阻断发布。
- 人工反馈能回流到评测集。

## 5. 模块级改造建议

### 5.1 ChatController

建议：

- 拆分普通聊天、流式聊天、AIOps 分析三个 Controller。
- 内部 DTO 移到独立 `dto` 包。
- 替换内存会话。
- 替换无界线程池。
- 引入统一异常处理。
- SSE 增加心跳、取消、断线处理和任务状态查询。

### 5.2 AiOpsService

建议：

- 将 prompt 外置并版本化。
- 将 Planner/Executor 输出改为强 schema。
- 分析流程从同步 invoke 改为异步任务。
- 保存每轮 Planner/Executor 状态。
- 最终报告不要只从 `planner_plan` 取文本，应从结构化状态生成。

### 5.3 QueryMetricsTools

建议：

- 增加 PromQL 查询工具。
- 支持按 alertname、service、namespace、instance 查询指标上下文。
- 返回原始查询语句和时间范围。
- 对 Prometheus API 失败做错误码分类。

### 5.4 QueryLogsTools

建议：

- 实现真实 CLS 查询。
- 支持时间范围、topic、query、limit、region。
- 对 region、topic、query 做参数校验。
- 对敏感字段脱敏。
- 查询结果返回可引用 evidence id。

### 5.5 InternalDocsTools 和 RAG

建议：

- 工具返回 source、chunkId、score、version、权限标签。
- 增加 rerank 和最小分数阈值。
- 对无检索结果的情况明确返回低置信度。
- 文档索引与上传解耦。

### 5.6 VectorIndexService

建议：

- 索引任务异步化。
- 支持批量 embedding。
- 加失败重试和断点续传。
- 记录文档版本和索引状态。
- collection/schema 变更走迁移流程。

### 5.7 FileUploadController

建议：

- 文件名随机化。
- 防路径穿越。
- 增加文件大小限制。
- 增加 MIME 和内容检测。
- 上传成功不等于索引成功，应返回索引任务 ID。

### 5.8 配置和部署

建议：

- 增加 `application-dev.yml`、`application-test.yml`、`application-prod.yml`。
- prod profile 禁止 mock。
- prod profile 禁止默认 API key。
- devtools 只在本地开发启用。
- 增加 Dockerfile、健康检查、部署脚本。

## 6. 推荐的目标架构

```text
用户 / OnCall
   |
   v
API Gateway / Auth / Rate Limit
   |
   v
OpsMind API Service
   |
   +-- Chat Service
   +-- AIOps Task Service
   +-- Report Service
   +-- Knowledge Service
   |
   v
Task Queue / Worker Pool
   |
   +-- Planner Agent
   +-- Executor Agent
   +-- Tool Runtime
   |
   +-- Prometheus / Metrics
   +-- CLS / Logs
   +-- Trace System
   +-- CMDB
   +-- Ticket System
   +-- Runbook KB
   |
   v
Persistence
   |
   +-- PostgreSQL: tasks, sessions, reports, audit logs
   +-- Redis: short-lived state, locks, stream status
   +-- Object Storage: uploaded docs
   +-- Milvus: vector index
   |
   v
Observability
   |
   +-- Metrics
   +-- Logs
   +-- Traces
   +-- Cost and quality dashboards
```

## 7. 上线前检查清单

### 7.1 功能检查

- [ ] 真实 Prometheus 告警查询可用。
- [ ] 真实日志查询可用。
- [ ] RAG 文档上传、索引、检索可用。
- [ ] Agent 报告包含证据引用。
- [ ] 工具失败时报告不会编造结论。
- [ ] SSE 断线后可查询任务结果。

### 7.2 安全检查

- [ ] API 已接入认证。
- [ ] 已有 RBAC 权限控制。
- [ ] 文件上传有大小和类型限制。
- [ ] 日志和报告做敏感信息脱敏。
- [ ] 高风险操作需要人工审批。
- [ ] 审计日志完整。

### 7.3 可靠性检查

- [ ] 无界线程池已替换。
- [ ] 外部工具调用有超时、重试、熔断。
- [ ] 会话和任务状态已持久化。
- [ ] 多实例部署下状态一致。
- [ ] 依赖服务不可用时有降级策略。

### 7.4 可观测性检查

- [ ] Actuator health/readiness/liveness 可用。
- [ ] Prometheus 可采集服务指标。
- [ ] Agent 任务耗时、成功率、失败原因可观测。
- [ ] LLM token、耗时、费用可统计。
- [ ] 工具调用成功率可告警。

### 7.5 质量检查

- [ ] 单元测试覆盖核心服务。
- [ ] Controller 测试覆盖主要 API。
- [ ] 工具层有 Mock 和真实模式测试。
- [ ] RAG 有离线评测集。
- [ ] Agent prompt 和 schema 变更有回归测试。
- [ ] CI 阻断失败测试。

## 8. 建议的近期迭代顺序

第一阶段，1 到 2 周：

1. 增加 prod profile，生产环境禁止 mock 和默认 key。
2. 实现真实 CLS 查询或接入目标日志平台。
3. 替换无界线程池。
4. 增加统一异常处理。
5. 增加 Actuator 和基础指标。

第二阶段，2 到 4 周：

1. 引入任务表、报告表、工具调用审计表。
2. 将 `/api/ai_ops` 改为异步任务。
3. Agent 输出改为结构化 schema。
4. RAG 文档索引异步化。
5. 增加核心 API 和工具层测试。

第三阶段，4 到 8 周：

1. 接入认证和权限。
2. 建立历史事故评测集。
3. 增加报告证据链和人工反馈机制。
4. 建立 CI/CD、Dockerfile、部署探针。
5. 对接工单或 IM，形成 OnCall 闭环。

## 9. 最终建议

当前项目不建议直接作为生产 OnCall Agent 上线，但非常适合作为生产方案的原型基础继续演进。

优先级最高的不是继续增加更多 Agent，而是先补齐真实数据、证据链、任务持久化、资源控制、安全权限和可观测性。只有这些底座稳定后，多 Agent 编排、RAG 和自动化建议才有生产价值。

推荐下一步先做 P0 改造，把“能演示”升级为“能接真实告警并给出可审计报告”。
