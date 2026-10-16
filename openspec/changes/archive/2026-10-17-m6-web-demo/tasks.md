# Tasks

## 模型批次说明

- 高能力实现批次：`gpt-5.6-sol / 中等`，处理规划、公共 API、SSE/异步竞态、安全 DOM、Maven/Docker、集成排错和最终回归审查。
- 低能力连续批次：`gpt-5.6-luna / 中等`，一次性处理 UI 样式、基础页面、localStorage、固定测试、README、截图和常规浏览器验收。
- 低能力批次出现公共契约、安全、竞态、构建、arm64 或连续两次修复失败时，必须升级到高能力模型；M6 结束后由高能力模型集中审查一次。

## 1. 前端契约与测试基础

- [x] 1.1 【模型：低能力连续批次】补齐并锁定前端开发依赖、pnpm lockfile 和 Vitest jsdom 配置，运行 `pnpm install --frozen-lockfile` 与一个空环境测试确认 Apple Silicon Node 22 可用
- [x] 1.2 【模型：高能力实现批次】依据 Java record 和真实 API fixture 修正 `types.ts`，覆盖顶层 facts/findings、Network 序列化和错误体，并通过 `pnpm typecheck`
- [x] 1.3 【模型：高能力实现批次】修复 SSE 缓冲解析，使其支持任意网络分块、LF/CRLF、多行 data、UTF-8 尾块和失败事件，并用 Vitest 覆盖这些边界
- [x] 1.4 【模型：低能力连续批次】实现版本化 localStorage 历史模块，覆盖损坏数据、按 ID 去重、倒序和最多 10 条的单元测试

## 2. 页面状态与安全渲染

- [x] 2.1 【模型：低能力连续批次】完善语义化 HTML 与响应式 CSS，加入输入提示、加载/错误 live region、时间线、报告、历史和免责声明，并用键盘焦点及窄屏人工检查验证
- [x] 2.2 【模型：高能力实现批次】实现只使用安全 DOM API 的时间线、报告、finding/evidence、来源和局限渲染函数；为非直观边界逻辑添加中文注释，并用恶意 HTML fixture 验证不会执行或注入标记
- [x] 2.3 【模型：高能力实现批次】实现 `idle → running → success|error` 入口状态机、确定性输入校验、重复提交锁定和 AbortController，使用 Vitest 验证成功、失败、取消和旧请求不得覆盖新结果
- [x] 2.4 【模型：低能力连续批次】接入最近分析列表与 GET 历史查询，验证点击历史不会重新分析、404 可移除、刷新后索引仍存在
- [x] 2.5 【模型：低能力连续批次】使用完整 `analysis.completed` fixture 验证 severity/ruleId/confidence/evidence、无 finding 提示、LLM/降级/持久化标记均正确显示

## 3. 构建与单一制品

- [x] 3.1 【模型：低能力连续批次】运行 `pnpm typecheck && pnpm test && pnpm build`，确认 `web/dist` 生产资源可复现生成且无未使用 TypeScript 代码
- [x] 3.2 【模型：高能力实现批次】在 `chainlens-app` 增加显式 Maven `web` profile，验证默认 `mvn verify` 不需要 Node，带 profile 的 package 会把前端产物放入 Boot 静态资源
- [x] 3.3 【模型：高能力实现批次】调整 Docker 多阶段构建并锁定兼容 linux/arm64 的 Node 镜像，验证 MacBook Air M5 上完整镜像无需宿主机 Node 即可构建

## 4. 端到端验收与文档

- [x] 4.1 【模型：高能力实现批次/里程碑审查】运行完整 Java `mvn verify`、前端测试和 OpenSpec strict 校验，确认 M0～M5 回归测试与 M6 新测试全部通过，并集中分析失败归因
- [x] 4.2 【模型：低能力连续批次】使用 M4 Anvil 数据在浏览器完成“输入 → SSE 时间线 → 报告 → evidence → 历史恢复”验收，并分别检查桌面与窄屏布局
- [x] 4.3 【模型：低能力连续批次】将桌面和窄屏演示截图保存到 `docs/screenshots/`，更新 README 的启动、开发、单制品和故障排查说明，并验证新用户仍可通过 Docker 路径在约 10 分钟内启动
- [x] 4.4 【模型：低能力连续批次】提供学习验收清单，让用户能够解释 SSE 为何用 fetch、独立修改一处报告渲染、复现一条 Vitest，并排查一次 API/流式失败；不得仅以代码生成或测试通过宣称已经掌握 M6
