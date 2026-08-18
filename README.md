# ChainLens AI

基于证据的 Ethereum 链上交易分析 Agent：输入交易哈希，从 Ethereum JSON-RPC 取证，用确定性规则引擎判定风险，由 AI 生成带证据（evidence）、规则编号（ruleId）、置信度（confidence）的中文结构化分析报告。

> ⚠️ 本工具基于公开链上数据自动生成分析报告，**不构成投资建议，也不是完整的安全审计**。

## 当前状态

🚧 **开发中**。进度：M0 脚手架 ✅、M1 取证与解析 ✅（34 测试）、M2 规则引擎 ✅（7 条确定性规则 + 35 个正/反/边界测试，共 69 测试全绿）。DeepSeek 工具调用冒烟待配置 API Key；本地嵌入冒烟待网络恢复。设计与里程碑见 [设计文档.md](设计文档.md)。

## 快速开始（V1 完成后生效）

```bash
cp .env.example .env   # 填入 DEEPSEEK_API_KEY（可选：无 Key 走降级报告）
docker compose --profile demo-offline up
```

## 技术栈

Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL + pgvector · Anvil · Vite

## 许可证

Apache-2.0，见 [LICENSE](LICENSE)。
