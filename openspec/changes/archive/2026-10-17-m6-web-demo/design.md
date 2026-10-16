# Design

## Context

参见 `proposal.md` 的 Why。`web/` 当前已有 HTML 骨架、严格 TypeScript 配置、API 客户端、类型声明和 SSE 解析器，但缺少 `main.ts`、样式、渲染与状态管理、历史存储、测试和制品集成。后端已经提供同步 POST、POST SSE、按 ID 查询和历史 SSE 重放；M6 不新增后端端点。

现有 SSE 读取器只按 `\n\n` 切分完整事件，需要补充 `\r\n\r\n`、任意分块边界和流尾部处理测试。前端类型也需要以 Java record 的实际 JSON 为准补齐 `facts`、顶层 `findings` 等字段，避免仅凭手写示例推断契约。

## Goals / Non-Goals

**Goals:**

- 让面试者在一分钟内看懂交易、处理步骤、风险规则、证据定位和降级状态。
- 保持前端体积和学习成本较低，让 Java 后端学习者能够独立解释和修改。
- 让关键 SSE、状态转换、报告渲染和历史行为可由 Vitest 重复验证。
- 同时支持 Vite 热更新、Maven 单制品和 Apple Silicon Docker 演示。

**Non-Goals:**

- 不建立通用组件库、复杂前端架构或全量 UI 覆盖率指标。
- 不让浏览器重新判断风险；前端只展示后端确定性 finding 和报告。
- 不用 localStorage 替代 PostgreSQL，浏览器只保存最近分析索引。

## Decisions

### Decision: 保持 Vanilla TypeScript + Vite

使用小型模块组织入口控制器、状态、渲染、历史和 SSE，不引入 React/Vue。M6 页面只有一个输入流程和少量区域，框架不会带来足够收益，反而增加依赖、抽象和面试解释成本。

替代方案是引入 React；它更适合大型交互页面，但不符合 V1 的“简单可演示 Web 页面”和避免不必要复杂度原则。

计划文件边界：

- `main.ts`：绑定 DOM、驱动分析状态机和事件路由；
- `render.ts`：只用 DOM API/textContent 渲染时间线、报告和错误；
- `history.ts`：版本化 localStorage 索引、去重、10 条上限和损坏数据恢复；
- `api.ts` / `sse.ts`：请求、AbortSignal 和健壮流解析；
- `types.ts`：与后端 JSON 契约一致的类型；
- `styles.css`：响应式、严重级别、焦点和 reduced-motion；
- `*.test.ts`：纯函数测试与 jsdom 关键交互测试。

### Decision: 使用显式的单分析状态机

页面状态限制为 `idle → running → success|error`。提交时清空旧时间线并禁用重复提交；新请求可通过 AbortController 取消旧流。SSE 事件按白名单路由，未知事件作为一般时间线消息保留，不导致页面崩溃。

替代方案是让各 DOM 事件直接修改任意区域；代码更短，但容易出现重复提交、旧流覆盖新报告和错误后按钮未恢复的问题。

### Decision: evidence-first 报告布局

首屏顺序为：交易与状态摘要 → Agent/SSE 时间线 → findings 卡片 → sources/limitations。颜色只作为辅助，severity、ruleId、confidence 和降级状态始终有文字。Evidence 默认显示关键字段，原始 RPC 方法与 JSON Pointer 可展开。

视觉方向采用克制的技术工作台风格，不使用“赌场式”涨跌动画或暗示收益的绿色/红色行情语义。报告为空时使用“未命中当前规则”，避免把有限规则集误表达为绝对安全。

### Decision: 历史只保存轻量索引

localStorage key 使用带版本号的命名，保存最多 10 条 `{analysisId, txHash, createdAt}`。点击后调用现有 GET 接口读取 PostgreSQL 结果。解析损坏时回退空列表；404 时允许移除单条索引。

替代方案是新增后端列表接口；它会扩大 M6 服务端契约和分页设计范围，现阶段没有必要。

### Decision: 所有不可信内容使用安全 DOM API

报告、错误、事件和链上字段只通过 `textContent` 或创建文本节点写入 DOM，禁止将 API 字符串传入 `innerHTML`。固定图标和静态结构可直接写在 `index.html`，但动态数据不拼接 HTML。

### Decision: 前端测试聚焦行为契约

Vitest 使用 jsdom 覆盖输入、SSE 分块、状态转换、severity 与 evidence 渲染、降级标记和 localStorage；不追求快照覆盖全部样式。构建验收依次执行 typecheck、test、Vite build、Maven `-Pweb` package 和浏览器端到端演示。

### Decision: 默认 Maven 构建与 Web 制品构建分离

默认 `mvn verify` 保持纯 Java，不要求 Node。显式 `-Pweb` profile 负责固定前端依赖、执行测试/构建并复制 `web/dist` 到 Boot 静态资源。Docker 使用兼容 linux/arm64 的固定 Node 22 多阶段构建，再在隔离的 Maven 构建上下文中组装静态资源；宿主机仍只需 Docker Desktop。

具体 Maven 插件和 Node 镜像版本在实现时以官方稳定版本核实并锁定，不能使用浮动 `latest`。本机 Node 22.23 满足现有 Vite 7 基线；构建过程应保留缓存层，避免每次重下 pnpm 依赖。

### Decision: 按任务批次分配模型能力

M6 不按文件或每个小任务频繁切换模型，而按风险和连续工作内容分成三段。模型名称以当前可用配置为准；本机当前可见的是 `gpt-5.6-sol`，未列出 `gpt-5.5-sol`，因此计划中的高能力默认写为 `gpt-5.6-sol / 中等`。

1. **高能力实现批次：`gpt-5.6-sol / 中等`**。负责规划、公共 API 契约、SSE/异步竞态、安全 DOM、Maven/Docker 集成和跨模块排错。对应任务为 1.2、1.3、2.2、2.3、3.2、3.3、4.1。这些任务一旦判断失败原因或边界错误，容易影响后端契约、数据安全或整条构建链，因此不交给低能力模型独立完成。
2. **低能力连续批次：`gpt-5.6-luna / 中等`**。一次性连续处理 UI 样式、基础页面结构、localStorage、固定 fixture 测试、README、截图和常规浏览器验收。对应任务为 1.1、1.4、2.1、2.4、2.5、3.1、4.2、4.3、4.4。批次内优先保持上下文连续，减少反复交接和额度消耗。
3. **里程碑集中审查：`gpt-5.6-sol / 中等`**。M6 实现批次结束后，集中审查公共契约、异步竞态、XSS 防护、构建可复现性、Apple Silicon 兼容性、测试结果和范围偏移，再决定是否进入归档。审查不替代用户学习验收。

低能力批次遇到以下情况必须升级到高能力模型：连续两次修复仍失败；涉及公共 API 或 Java record 契约变化；出现旧请求覆盖新请求、SSE 解析边界、XSS、Maven/Docker 构建或 arm64 兼容性问题；或者需要改变规格、设计或任务拆分。低能力批次完成后仍需由高能力模型做一次集中审查。模型能完成任务不等于用户已经掌握，4.4 的独立解释、修改、复现和排错仍是学习完成条件。

## Data Flow

```text
用户输入
  → 确定性哈希校验
  → POST /api/v1/analyses (Accept: text/event-stream)
  → SSE 解析缓冲区
  → 时间线事件路由
  → analysis.completed 中的 AnalysisResult
  → 报告安全渲染 + 最近 ID 写入 localStorage

最近 ID
  → GET /api/v1/analyses/{id}
  → 持久化 AnalysisResult
  → 报告安全渲染（不重新分析）
```

## Risks / Trade-offs

- [SSE 事件被任意网络边界切开] → 使用持续缓冲区并测试 LF、CRLF、多 data 行、UTF-8 与尾部事件。
- [旧请求覆盖新请求] → 每次提交持有请求 token/AbortController，只允许当前请求更新最终状态。
- [前后端类型漂移] → 以 Java record 和真实 JSON fixture 为依据；测试完整 `analysis.completed` payload。
- [LLM 文本造成 XSS] → 动态字符串统一 textContent，加入恶意标记测试。
- [前端工具链拖慢 Java 构建] → 默认 Maven 生命周期不启用 web profile，Docker/Maven 缓存依赖。
- [Maven profile 在不同机器缺 pnpm] → `-Pweb` 的错误信息说明前置条件；Docker 路径不依赖宿主机 Node，README 分开说明两条路径。
- [历史只在当前浏览器可见] → 明确这是演示便利功能，真实历史仍由 PostgreSQL 和按 ID 接口负责。

## Migration Plan

1. 先完成类型、SSE 与 localStorage 纯逻辑及测试，再接 UI，避免视觉代码掩盖协议问题。
2. 完成入口状态机与安全渲染，使用固定 fixture 通过 Vitest。
3. 接入真实 M4 Anvil 环境，确认时间线、报告、历史和降级状态。
4. 增加 Maven `-Pweb` 与 Docker 多阶段构建，保持默认 Java 构建不变。
5. 在桌面和窄屏浏览器验收，保存截图并更新 README。

回滚时可移除 web profile、Docker 前端阶段和静态资源，不影响已有 REST/SSE API 与离线后端演示。
