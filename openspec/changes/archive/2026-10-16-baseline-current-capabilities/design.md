# Design

## Context

参见 `proposal.md` 的 Why。当前仓库已经实现 M0～M5 的主体能力，但尚无 OpenSpec 主规范；《设计文档》描述目标架构，README 描述当前状态，代码和测试提供实现证据。此次只建立规范基线，不改变运行时。

## Goals / Non-Goals

**Goals:**

- 用稳定的外部行为边界组织当前能力，而不是复制 Java 模块结构。
- 只纳入可从代码、测试或 M4 演示核实的行为。
- 为后续 change 提供可增量修改的主规范。
- 保留《设计文档》作为总体架构和里程碑事实源。

**Non-Goals:**

- 不把 OpenSpec 当作《设计文档》的缩写或替代品。
- 不修改实现，不新增依赖，不执行尚未授权的真实模型调用。
- 不宣称用户已经掌握项目；学习验收仍需独立解释、复现、修改和排错。

## Decisions

### Decision: 按用户可观察能力划分五个规范

规范分别覆盖链上证据、确定性风险、API 与历史、本地演示、Agent 报告编排。这样内部模块可以重构而不迫使需求路径变化。

替代方案是按九个 Maven 模块各建一个规范；该方案会把实现结构误当成产品契约，产生重复和脆弱的 delta，因此不采用。

### Decision: 基线只记录已核实的现状

真实 DeepSeek 演示仍是《设计文档》M5 的验收要求，但当前仓库证据只能确认 smoke 测试代码存在和离线 FakeChatModel 测试存在，不能证明真实调用已经成功。因此主规范描述通用 ChatModel 行为，不声称特定 Provider 已完成验收。

替代方案是直接把 README 的“M5 已完成”完整复制为规范；这会把状态陈述误当成验证证据，因此不采用。

### Decision: 通过完成并归档一次基线 change 生成主规范

使用标准 `proposal → specs → design → tasks` 流程建立 delta，完成严格校验后同步到 `openspec/specs/` 并归档。后续 M6～M9 只提交相对这些主规范的变化。

替代方案是直接手写主规范；该方案缺少变更来源和归档记录，不利于教学和追踪，因此不采用。

### Decision: 不引入新的本地运行负担

OpenSpec 只增加 Markdown/YAML 规划文件和 Codex 工作流技能，不增加应用进程、Docker 服务或编译依赖。它不会改变 MacBook Air M5 的内存占用和 arm64 运行兼容性。

## Risks / Trade-offs

- [基线遗漏已有边界行为] → 使用《设计文档》、README、实现和测试交叉核对；以后发现遗漏时通过独立 change 增补。
- [规范与实现继续漂移] → 每个里程碑在实施前创建 change，完成后以测试证据更新任务并归档。
- [把计划中的能力误写为已实现] → 基线只写当前可核实行为；M6～M9 保持活动变更或未来里程碑状态。
- [真实模型验收缺口被忽略] → 在基线 proposal 和 design 中显式保留该缺口，不将其包装为已完成。

## Migration Plan

1. 严格校验基线 change 的 proposal、五个 delta specs、design 和 tasks。
2. 将五个 delta specs 同步为 `openspec/specs/` 下的主规范。
3. 将基线 change 归档，保留完整决策记录。
4. 创建 `m6-web-demo` 活动变更，后续实现只以其 tasks 为执行边界。

若需要回滚，只删除新增的 OpenSpec 文件即可；应用代码、数据库和 Docker 运行环境不受影响。
