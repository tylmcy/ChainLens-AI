# Web Analysis Demo Specification

## Purpose

定义 ChainLens 的浏览器演示体验，使用户无需理解 curl 即可观察交易分析进度、核对风险证据、识别降级状态并重新打开最近结果。

## Requirements

### Requirement: 接受交易哈希或含哈希问句
Web 页面 SHALL 接受完整 Ethereum 交易哈希或包含该哈希的自然语言问句，在提交前完成空值和哈希格式反馈，并且不得用 LLM 理解或改写输入。

#### Scenario: 提交完整交易哈希
- **WHEN** 用户输入 `0x` 加 64 位十六进制字符并点击分析
- **THEN** 页面向现有分析接口提交请求并进入运行状态

#### Scenario: 从问句提取哈希
- **WHEN** 用户输入一段包含且仅包含一个有效交易哈希的问句
- **THEN** 页面使用确定性正则识别该哈希并允许提交原始问句

#### Scenario: 输入不包含有效哈希
- **WHEN** 用户提交空内容或不含完整交易哈希的内容
- **THEN** 页面在输入区域展示中文错误且不发出网络请求

### Requirement: 实时展示 SSE 分析时间线
Web 页面 SHALL 使用 POST 流式请求按接收顺序展示阶段、规则完成、工具开始、工具完成、报告完成和分析完成事件，并能正确处理跨网络分块及 CRLF/LF 两种 SSE 分隔符。

#### Scenario: 工具调用跨多个数据块到达
- **WHEN** 一个 SSE 事件被拆分在多个网络数据块中
- **THEN** 页面只在事件完整后渲染一次，且不丢失、重复或截断 JSON data

#### Scenario: 分析成功完成
- **WHEN** 页面收到 `analysis.completed` 事件
- **THEN** 页面结束运行状态、保留完整时间线并展示该事件携带的最终分析结果

#### Scenario: 分析流返回失败事件
- **WHEN** 页面收到 `analysis.failed` 或连接以错误结束
- **THEN** 页面停止加载、展示可理解的错误与重试入口，且不伪造报告

### Requirement: 以证据优先方式展示结构化报告
Web 页面 MUST 展示摘要、交易状态、网络、LLM/降级/持久化状态、actions、findings、sources 和 limitations；每项 finding MUST 展示 ruleId、severity、confidence 及可展开的 evidence 原始定位。

#### Scenario: 报告包含风险 finding
- **WHEN** 最终报告包含一个或多个 finding
- **THEN** 页面按严重级别提供文字标签和视觉区分，并允许查看交易哈希、RPC 方法、JSON Pointer、日志索引和原始数值信息

#### Scenario: 报告没有风险 finding
- **WHEN** 最终报告的 findings 为空
- **THEN** 页面明确显示“未命中当前规则”，而不是暗示交易绝对安全

#### Scenario: 分析使用确定性降级
- **WHEN** 最终结果为 `llmUsed:false` 且 `degraded:true`
- **THEN** 页面显著标记“确定性降级报告”，同时正常展示规则结论和证据

### Requirement: 保存并恢复最近分析
Web 页面 SHALL 在 localStorage 中最多保存 10 条最近成功返回的分析索引，并通过已有按 ID 查询接口恢复历史结果；本地索引不得保存 API Key、私钥或完整原始 RPC 文档。

#### Scenario: 完成一次新分析
- **WHEN** 页面收到带 analysisId 的最终结果
- **THEN** 页面将 analysisId、交易哈希和创建时间放到最近历史顶部，按 ID 去重并裁剪到 10 条

#### Scenario: 打开历史记录
- **WHEN** 用户点击一条最近分析
- **THEN** 页面调用历史查询接口并展示持久化报告，不重新发起链上分析

#### Scenario: 历史记录已经失效
- **WHEN** 历史查询返回不存在或无法读取
- **THEN** 页面提示该记录不可用并允许从本地历史移除

### Requirement: 页面具备基本可访问性和安全渲染
Web 页面 MUST 支持键盘提交、清晰焦点、语义化状态提示、窄屏布局和减少动态效果偏好；来自链上、数据库或 LLM 的文本必须按纯文本渲染，不得作为 HTML 注入标记。

#### Scenario: 使用键盘和屏幕阅读器操作
- **WHEN** 用户只使用键盘提交分析并等待状态变化
- **THEN** 焦点顺序合理，运行状态和错误通过可感知区域更新，控件具有可理解名称

#### Scenario: 报告文本包含 HTML 标记
- **WHEN** API 返回的摘要、标题或说明中包含类似 HTML 的字符
- **THEN** 页面将其显示为文本而不执行标签或脚本

### Requirement: 前端可以独立开发并集成到单一制品
系统 SHALL 支持 Vite 开发服务器代理现有 API，也 SHALL 支持通过 Maven `web` profile 将生产构建产物打入 Spring Boot 静态资源；Docker 完整构建在 Apple Silicon 上不得要求宿主机安装 Node。

#### Scenario: 本地前端开发
- **WHEN** 开发者运行前端开发命令且后端监听默认地址
- **THEN** 浏览器页面通过同源代理访问 API 和 SSE，无需 CORS 特例

#### Scenario: 构建单一应用制品
- **WHEN** 开发者执行带 `web` profile 的 Maven 构建或完整 Docker 构建
- **THEN** 生成的 Spring Boot 应用可在根路径提供生产前端页面及其静态资源

#### Scenario: 默认 Java 构建
- **WHEN** 开发者执行不带 `web` profile 的常规 Maven 测试或打包
- **THEN** 构建不要求 Node/pnpm，现有 Java 开发反馈速度不受前端工具链阻塞
