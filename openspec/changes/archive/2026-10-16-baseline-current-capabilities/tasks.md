# Tasks

## 1. 现状取证

- [x] 1.1 对照《设计文档》、README、M0～M5 提交和相关测试识别当前能力，并以能力边界而不是 Maven 模块划分规范
- [x] 1.2 核实 M5 的真实 DeepSeek 验收证据不足，并在 proposal 与 design 中保留该缺口

## 2. 基线规范

- [x] 2.1 编写五个 capability delta spec，并检查每项 Requirement 至少包含一个可观察的 WHEN/THEN Scenario
- [x] 2.2 编写基线迁移设计，说明主规范生成方式、Apple Silicon 影响、回滚和规范漂移风险

## 3. 校验与展示

- [x] 3.1 运行 `openspec validate baseline-current-capabilities --strict` 并确认所有 artifact 通过严格校验
- [x] 3.2 运行 `openspec show baseline-current-capabilities --type change`，确认用户可从 OpenSpec 查看完整基线变更
