# Formula Master — Claude Code 入口

> **本项目的完整说明书统一维护在 [`AGENTS.md`](AGENTS.md)（工具无关权威底本）。**
> 请**现在就完整阅读 `AGENTS.md`**，它包含：项目概况、当前现状、技术栈、架构铁律、包结构、
> 踩坑方法论、M3 规范，以及开工必读顺序。
>
> 之所以正文放在 `AGENTS.md` 而非这里：项目要能无痛迁移到其它 AI 工具（Cursor / Copilot / …），
> 故用工具无关的 `AGENTS.md` 当唯一真相源，本 `CLAUDE.md` 只作 Claude Code 的指路桩，避免多份漂移。

## 必读（按顺序）

1. [`AGENTS.md`](AGENTS.md) — 项目权威说明（先读完这个）
2. [`docs/ai-context/协作约定.md`](docs/ai-context/协作约定.md) — 怎么跟这个用户干活（沟通/决策/测试分工/产品铁律）
3. [`TODO.md`](TODO.md) — 阶段全景 + 当前 Sprint 6 欠债清单
4. [`docs/design/架构总览.md`](docs/design/架构总览.md) — 当前架构与状态全景
5. [`docs/ai-context/环境与工具.md`](docs/ai-context/环境与工具.md) — android CLI / skills / 真机 DB 调试

> Claude Code 私有 auto-memory 索引：`~/.claude/projects/-home-houtas-StudioProjects-FormulaMaster/memory/MEMORY.md`
> （个人快速索引；耐用知识以仓库内 `AGENTS.md` + `docs/` 为准，见 `docs/ai-context/协作约定.md` 维护约定）。
