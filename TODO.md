# Formula Master — 项目导航

> **📍 当前位置**：学习流程重构阶段（03）· Sprint 1-5 主干完成，**Sprint 6 收尾进行中**（已收：C5 卡型✅ / Step2 拆块✅ / 手写召回✅ / 空值驱动隐藏✅ / 默写 hint 分级✅（后四项代码完成待真机）；剩：Step5 例题 · Step7 迷你卡 · StudyPhase 真机，详见下方「收尾清单」）。
>
> 本文件是**指针 + 全局结构图**。查具体任务进度请打开对应阶段 TODO。

---

## 🗺️ 阶段结构全景（大阶段 = 独立文件 → Sprint → Task）

```
TODO.md  ← 你在这里（根指针，不含任务）
│
docs/phases/
│
├─ 01_原型阶段_TODO.md              ✅ 已归档（+ summaries/原型阶段总结.md）
│   └─ Sprint 1 基础设施 / 2 渲染引擎+数据预置 / 3 记忆模块 /
│      4 复习模块+FSRS / 5 测试模块+统计 / 6 自动化+打磨
│
├─ 02_打磨阶段_TODO.md              ✅ 已归档（+ summaries/打磨阶段总结.md）
│   └─ Sprint 1 手写识别落地 / 2 性能修复+时间设置 / 3 输入方式+反馈重构
│
└─ 03_学习流程重构_TODO.md          🔵 当前阶段（未归档·无总结）
    ├─ Sprint 1 数据基础+Scene/Subject+6类子卡schema          ✅ 完成
    ├─ Sprint 2 七步仪式+复习路由器+C1/C2/C3+子卡FSRS          ✅ 主干（七步 Step2/5 占位见收尾清单）
    ├─ Sprint 3 C4/C6卡型+错题反向UI+C2判错着色+Leech          ✅ 完成
    ├─ Sprint 4 数据层地基标签化+公式族图谱(记忆主视图)        ✅ 完成
    ├─ Sprint 5 StudyPhase 学习阶段切换                        ✅ 代码完成（真机验收部分待做）
    ├─ Sprint 6 收尾（C5卡型 / 七步占位补齐 / 代码半落地 / 真机） 🔵 进行中
    └─ 内容工程 Track A/B/C/D（独立于代码 Sprint，可并行）
```

---

## 🔧 Sprint 6 收尾清单（当前欠债 · 逐条可核查）

> 详细验收标准与留尾见 [`03_学习流程重构_TODO.md` § Sprint 6](docs/phases/03_学习流程重构_TODO.md)。

| # | 项 | 现状 | 还差 |
|---|---|---|---|
| 1 | **C5 易混辨析卡** | ✅ 完成 + 真机验收通过（`719fbc8`） | — |
| 2 | **七步 Step 2 拆块讲解** | ✅ 完成 + 真机验收通过（`dd83d56`） | — |
| 3 | **七步 Step 5 Worked Example** | 🔴 占位（`workedExamples` 字段不存在） | 字段 + 内容 + 例题 UI |
| 4 | **七步 Step 7 迷你卡** | 🟡 C1/C2/C3 ✅；C4/C5/C6 mini 形态"自动通过" | C4/C5/C6 mini 卡形态（空值处理复用 6.5 规则） |
| 5 | **板块/卡片空值驱动隐藏（原 #323）** | ✅ 代码完成（2026-07-21，`ReviewCardAvailability` + 仪式动态步数）；⏳ 真机验收待 | 造缺板块数据的公式真机验（少步/不出卡/不显占位）|
| 6 | **StudyPhase 真机验收** | 🟡 设置切换已验 | 新卡上限拦截 + 复习间隔/交错随阶段变化 真机验 |
| 7 | **复习默写环节** | ✅ 代码完成（2026-07-21：6.8 接输入 + hint 分级渐进 chunks 驱动）；⏳ 真机验收待 | 真机验连错递进揭示 + 错3 blocked |

> 备注：#3 卡在**内容/数据字段**（`workedExamples` 60 道例题），需与内容工程 Track 协同。
> Scene 三态（Gaokao/SelfStudy）为**规划外留位**，不计入本阶段债。

---

## 🎯 当前活动文档

| 文件 | 作用 | 使用时机 |
|------|------|---------|
| 🏛️ [`docs/design/架构总览.md`](docs/design/架构总览.md) | **架构 + 状态全景**（对接现状入口） | 新对话开工第一站 / 查当前架构真相 |
| 📜 [`docs/planning/RFC_学习流程重构.md`](docs/planning/RFC_学习流程重构.md) | **阶段 RFC**（设计底稿） | 启动新 Sprint 前必读 / 决策回溯 |
| 📄 [`docs/phases/03_学习流程重构_TODO.md`](docs/phases/03_学习流程重构_TODO.md) | **当前阶段 TODO** | 查看 Sprint 进度 / 标记 Task 完成 |
| 📥 [`docs/planning/改进点池.md`](docs/planning/改进点池.md) | **改进点收集池** | 随时追加新想法 / Sprint 规划时选条目 |
| 📚 [`docs/research/全流程设计研究报告.md`](docs/research/全流程设计研究报告.md) | **认知科学 + 产品设计研究报告** | RFC 的研究底稿 |
| 📐 [`docs/design/`](docs/design/) | **当前架构权威**（数据模型 / 技术规格 / UI 规范） | 写代码前查 schema / 约定 |

---

## 🗄️ 已归档

| 阶段 | TODO 归档 | 阶段总结 |
|------|----------|---------|
| 01 原型阶段 | [`docs/phases/01_原型阶段_TODO.md`](docs/phases/01_原型阶段_TODO.md) | [`docs/phases/summaries/原型阶段总结.md`](docs/phases/summaries/原型阶段总结.md) |
| 02 打磨完善阶段 | [`docs/phases/02_打磨阶段_TODO.md`](docs/phases/02_打磨阶段_TODO.md) | [`docs/phases/summaries/打磨阶段总结.md`](docs/phases/summaries/打磨阶段总结.md) |

> ⚠️ `docs/phases/01·02` 及其总结为**冻结的历史归档**——其中对旧文档名（`Project_Spec.md` / `核心数据库和算法设计.md` 等）与章节号的引用是当时记录，**不代表现状**。对接现状请读 `docs/design/`。

---

## 🔄 工作流（新对话必读）

1. **打开项目** → 读 `CLAUDE.md` + 本文件 + 当前阶段 TODO
2. **新想法冒出** → 追加到 `docs/planning/改进点池.md` 的"待评估"分区
3. **Sprint 收尾 / 启动新 Sprint** → 扫描改进点池，按 (优先级 ASC, 提出时间 ASC) 挑选 4-8 条进入新 Sprint，被纳入的条目移动到"已纳入"分区
4. **Task 完成** → 在当前 Sprint Task 列表打勾（**勾必须可核查**：写清 commit / 文件 / 留尾，不写笼统"✅ 完成"），全部完成后写 Sprint 总结
5. **阶段收尾** → 写阶段总结文档，TODO 归档到 `docs/phases/0X_XXX_TODO.md`，开新阶段

> **写 TODO 铁律**（2026-07-13 用户要求）：条目要**具体、可核查、拆到能验收的粒度**，占位/延后/回落必须显式标 🔴/🟡 并写清"还差什么"，不写笼统乐观勾——否则会遗漏（本次就漏了 C5 整卡 + 七步 2.5 步占位）。
