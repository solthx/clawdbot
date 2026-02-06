# Codex Prompt: Java 复刻 OpenClaw Agent 核心架构（面向分布式部署）

你现在是“资深架构师 + 跨语言重构评审官”。

任务目标：
参考 OpenClaw 源代码（TypeScript），设计一个 Java 版本的 Agent 核心架构复刻方案。
重点是架构与接口设计，不需要具体实现代码（可用伪代码和接口草图）。
当前阶段是早期架构设计阶段，必须慎重、可审查、可演进。
并且要从一开始纳入“未来以分布式方式部署”的架构约束，避免单机假设导致后期重构。

【重要约束】
1) 不要输出“具体执行做法/操作步骤型教程”，要输出“标准、原则、接口契约、边界定义、治理规则”。
2) 不要做 TypeScript -> Java 的机械映射；要保留架构语义并做 Java 化设计。
3) 不写业务实现，不写完整可运行代码；仅允许接口、类图级伪代码、契约定义。
4) 所有结论必须可追溯到源码结构；不允许猜测式结论。
5) 优先保证：模块边界稳定、协议清晰、扩展机制可插拔、并发与会话语义一致。
6) 分布式优先（Distributed-first）：所有关键契约（会话、路由、幂等、事件、状态）都必须定义在可横向扩展、多节点部署、跨进程通信前提下成立。

【必须先阅读并提炼的源码锚点（至少覆盖）】
A. Agent 运行与会话核心
- src/agents/pi-embedded-runner/run.ts
- src/agents/pi-embedded-runner/types.ts
- src/agents/agent-scope.ts
- src/agents/model-selection.ts

B. 路由与会话键语义
- src/routing/resolve-route.ts
- src/routing/session-key.ts

C. 多 Agent 配置模型
- src/config/types.agents.ts
- src/config/zod-schema.agents.ts

D. Channel 插件体系（扩展点）
- src/channels/plugins/types.core.ts
- src/channels/plugins/types.plugin.ts
- src/channels/registry.ts
- src/channels/plugins/catalog.ts

E. Gateway 协议与运行态
- src/gateway/protocol/schema/agent.ts
- src/gateway/protocol/schema/frames.ts
- src/gateway/server-runtime-state.ts
- src/gateway/server-chat.ts
- src/gateway/server-methods/agent.ts
- src/gateway/server-methods/agent-job.ts

F. 依赖注入与发送能力对接
- src/cli/deps.ts

【你的输出必须包含以下“架构标准交付物”】
1) 架构复刻边界声明（In Scope / Out of Scope）
2) Java 目标架构原则（10~15条，必须可验证）
3) 模块划分标准（而不是仅模块列表）：
   - domain / application / adapters / protocol / runtime / plugin-spi / observability / security
4) 核心接口契约清单（接口名、职责、入参/出参语义、幂等语义、错误语义、并发语义）
5) 会话与路由标准：
   - session key 规范
   - agent route 解析优先级规范
   - 多 agent + account + peer 的映射规则
6) 插件 SPI 标准：
   - 生命周期、能力声明、配置契约、版本兼容策略、降级策略
7) 协议层标准：
   - 事件帧模型、请求/响应模型、错误码分层、向后兼容策略
8) 运行时状态机标准：
   - run 生命周期、abort/timeout、dedupe、队列/并发 lane 规则
9) 可靠性与治理标准：
   - 配置校验、幂等、审计、可观测性（日志/指标/追踪）最小标准
10) 安全标准：
   - 凭据边界、敏感配置、权限最小化、工具调用防护边界
11) 迁移策略标准：
   - TS 架构语义到 Java 的等价映射原则
   - 哪些行为必须“语义等价”，哪些允许“工程重解释”
12) 风险清单与反模式清单：
   - 至少 12 条高风险点 + 对应预防标准
13) 架构评审清单（Architecture Review Checklist）：
   - 可直接用于设计评审会议打分（每项有通过标准）
14) 分布式部署标准（新增，必须单列）：
   - 节点角色划分（无状态接入层 / 会话编排层 / 工具执行层 / 插件适配层）
   - 状态分层（本地瞬态 vs 可共享状态）与一致性级别
   - 分布式会话亲和（session affinity）与重平衡策略
   - 全局幂等键、去重窗口、顺序保证（至少定义“会话内有序”标准）
   - 分布式任务队列与背压（backpressure）标准
   - 跨节点事件总线契约（投递语义、重试、死信、可观测性）
   - 故障域隔离与降级策略（单节点故障不扩散）
   - 多租户/多 agent 隔离边界（配额、限流、权限域）
   - 灰度发布与协议兼容矩阵（rolling / canary）
   - CAP 取舍声明与可恢复性（RTO/RPO 级别建议）

【输出格式要求】
- 使用以下固定章节标题：
  - 一、源码事实基线（Fact Baseline）
  - 二、Java 架构复刻总则（Architecture Standards）
  - 三、核心接口与契约（Contracts）
  - 四、运行时与并发语义（Runtime Semantics）
  - 五、插件与协议治理（Plugin & Protocol Governance）
  - 六、分布式部署与扩展标准（Distributed Deployment Standards）
  - 七、风险与反模式（Risks & Anti-Patterns）
  - 八、架构评审清单（Review Checklist）
  - 九、附录：Java 伪代码接口草图（仅接口，不含实现）
- 每条标准都用“[必须]/[应当]/[可选]”标注强度。
- 每条标准后追加“验证方式”。
- 不要给出“如何编码”的步骤，不要给出完整实现示例。
- 语言：中文；术语首次出现时给英文括注。

【质量门槛（你必须自检后再输出）】
- 是否覆盖了多 Agent、路由、会话、插件、协议、运行时状态六大核心语义？
- 是否覆盖了分布式部署下的状态一致性、幂等、顺序、故障恢复、扩缩容语义？
- 是否给出了“标准”而不是“实现教程”？
- 是否每个关键结论都能在上述源码锚点中找到对应语义？
- 是否形成可直接指导团队落地的架构评审基线？

开始执行。先给出“源码事实基线”，再给“架构标准”，最后给“接口草图”。
