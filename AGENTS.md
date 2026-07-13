# AGENTS.md — 任意 AI 助手的统一入口

> 这个文件是 **AI 协作的工具无关入口**。无论你是 Claude Code / Cursor / Copilot / 其它，
> 打开本仓库先读这里，再按顺序读下面的文件，就能接上项目上下文与协作约定。
> （各工具专用规则文件——`CLAUDE.md` / `.cursorrules` / `.github/copilot-instructions.md`
> ——应只写一句"请先阅读 AGENTS.md 及其指向的文件"，正文统一维护在这里，避免多份漂移。）

## 项目一句话

Formula Master：考研数学**公式记忆助手**（Android · Kotlin + Jetpack Compose + Room + KaTeX）。
长期愿景是做成领域无关的记忆「外壳」，但**眼下一切以考研数学核心功能为准**。

## 开工必读顺序

1. **本文件** — 协作总入口
2. [`docs/ai-context/协作约定.md`](docs/ai-context/协作约定.md) — **怎么跟这个用户干活**（沟通/决策/测试分工/产品铁律）🔥
3. [`CLAUDE.md`](CLAUDE.md) — **技术栈 + 架构铁律 + 包结构 + 踩坑方法论**（工具无关，任何 AI 都该遵守）
4. [`TODO.md`](TODO.md) — **阶段结构全景 + 当前欠债清单**（当前在 03 阶段 Sprint 6 收尾）
5. [`docs/design/架构总览.md`](docs/design/架构总览.md) — 当前系统架构与状态全景（对接现状权威）
6. [`docs/ai-context/环境与工具.md`](docs/ai-context/环境与工具.md) — 本机 `android` CLI / 已装 skills / 真机 DB 调试流程

## 深入时按需读

- [`docs/planning/RFC_学习流程重构.md`](docs/planning/RFC_学习流程重构.md) — 当前阶段设计底稿（所有决策来源）
- [`docs/planning/改进点池.md`](docs/planning/改进点池.md) — 新想法收集 + 已拒绝/搁置决策
- [`docs/phases/03_学习流程重构_TODO.md`](docs/phases/03_学习流程重构_TODO.md) — 当前阶段逐 Task 进度
- [`docs/research/全流程设计研究报告.md`](docs/research/全流程设计研究报告.md) — 认知科学/产品研究底稿

## 一句话现状（更新即改）

> **03 学习流程重构阶段 · Sprint 1-5 主干完成 · Sprint 6 收尾进行中（7 块债，见 TODO.md）。**
