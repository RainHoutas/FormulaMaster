---
name: Formula Master 学习流程重构 RFC
description: 基于《考研数学公式学习—复习—考试全流程设计研究报告》的产品级重构方案
created: 2026-05-08
status: 已拍板（待动手实施）
related:
  - docs/research/全流程设计研究报告.md
  - docs/phases/03_学习流程重构_TODO.md
---

# RFC：学习流程重构

> 本 RFC 把研究报告的认知科学结论 + 产品差异化创新与当前 Formula Master 的工程现状对接，
> 形成可拆分为多个 Sprint 的执行方案。

---

## 0. 元信息

- **状态**：用户已拍板核心 5 项决策（见 §5），细化决策待 Sprint 启动前补充
- **作用**：作为「学习流程重构阶段（03）」的产品/架构底稿，所有 Sprint 必须能回溯到本 RFC 某一节
- **范围**：核心引擎 + 卡型矩阵 + 复习路由 + 错题反向 + 公式族图谱 + 阶段切换 UI
- **不在范围**：FSRS 算法重写 / LaTeX 输入法 / 推导动画 / 全场景泛化（详见 §6）

---

## 1. 现状梳理

### 1.1 三 Tab 当前职责

| Tab | 现职责 | 数据流 |
|-----|--------|--------|
| 记忆（Memory） | 公式列表 + `FormulaDetailScreen`（KaTeX 渲染 + 临摹画板 + "激活"按钮） | `FormulaEntity` → `MemoryViewModel` |
| 复习（Review） | FSRS 调度的 `ReviewCard` 三步交互（遮罩 → 候选填空 → 4 档评分） | `StudyStateEntity` + `ReviewScheduler.calculate(...)` |
| 测试（Test） | 默写模式（手写识别 / 纸笔自评二选一），全公式默写 + 严裁决 + 反馈采样 | `OcrFeedbackEntity`（含 `wrongPlaceholdersJson`）+ FSRS 测试模式（×1.5 奖励 / 错误清零） |

### 1.2 已具备的核心能力

- ✅ FSRS（DSR 三组件）+ 截断到刷新整点 + DST 安全
- ✅ `FormulaEntity.clozeData` JSON 已能拆出 `placeholder` 列表（Sprint 3 已用于反馈多选）
- ✅ `FormulaEntity.derivationSteps` 字段预留（原型阶段未充分使用）
- ✅ `SprintModeManager`（仅算法层判定 + halveStabilityAbove，**无 UI 阶段切换**）
- ✅ Scene-agnostic 设置（刷新时刻 / 考试日期 / 输入方式都已抽象到 `AppPreference`）

### 1.3 当前用户学习路径

```
[Memory] 浏览公式列表 → 进 FormulaDetail → 临摹（有 30% 透明参考）→ 点"激活"
                                           ↓
                                 写入 StudyStateEntity（learningState=Learning）
                                           ↓
[Review] FSRS 推送 → 看遮罩 → 展开公式 → cloze 候选填空 → 4 档自评（FSRS 评分）
                                                           ↓
                                                     状态机 Learning → Reviewing → Mastered
                                                           ↓
[Test]  手写识别 / 纸笔自评（二选一）→ 严裁决 → 错则 lapses+1 + S 清零 + 回 Learning
                                                ↓
                                       OcrFeedback chip 多选记录错位 placeholder
```

---

## 2. 痛点列表（基于研究报告 + 用户洞察）

| # | 痛点 | 证据 | 报告对应 |
|---|------|------|---------|
| P1 | **卡型单一**：复习以"遮罩 + 候选填空"为主，缺多维度提取 | 当前 ReviewCard 仅一种交互形态 | 报告 §8 6 类卡片矩阵 |
| P2 | **缺条件警觉**：用错条件 = "标准零分"，但当前没有条件先行卡 | 武忠祥实战经验 | 报告 §4.3 Step 1 |
| P3 | **clozeData 用得粗**：现仅作为反馈的 placeholder 列表，未做加权挖空抽样 | `ClozeParser` 平等对待所有 placeholder | 报告 §5.2 加权抽样 |
| P4 | **题型缺位**：用户记公式但不知用在哪，记完不会做题 | TestScreen 是孤立默写场，无题面 | 报告 §5.4 题型反查 |
| P5 | **易混辨析缺位**：拉中柯西、二重三重、正态卡方等易混对不区分训练 | 当前无 confusable 字段，无 A/B 卡 | 报告 §5.3 辨析卡 |
| P6 | **推导未发挥**：`derivationSteps` 字段闲置，对"推导优先派"公式（诱导/和差）仍按死记处理 | 数据有，UI 无 | 报告 §5.5 5 秒推导挑战 |
| P7 | **阶段切换缺位**：一/二/三/冲刺只有冲刺有算法判定，没有 UI 指示 / 推送策略调整 | `SprintModeManager` 用户无感 | 报告 §3.2 三轮策略切换 |
| P8 | **错题反向链路缺位**：用户在错题本里发现"我这道题用错了 X 公式"，但没有任何入口把信号回灌给 X 的 retrievability | 完全未实现 | 报告 §7.4 错题反向 |
| P9 | **全程缺少题型语境**：编码阶段就应该让用户看到例题，但当前只有 latex + clozeData，没有 worked example | 用户问题 Q1 | 报告 §4.3 Step 4 |
| P10 | **场景假设固化**：默认"考研学生"且仅考研，未来扩展高考/研究者需大改 | 用户问题 Q3 | 报告 §3.2 场景策略 |

---

## 3. 设计原则

### 3.1 核心架构：Scene 三态

```
┌─────────────────────────────────────┐
│   通用引擎（FSRS + 6 类卡 + 拆块）   │  ← 所有 Scene 共用
└─────────────────────────────────────┘
                ↑
                │ 由 UseScene 决定行为开关
                │
┌─────────────────────────────────────┐
│  UseScene 枚举                       │
│   ├─ KaoyanMath（默认，本阶段做透） │
│   ├─ Gaokao         （仅留枚举位）   │
│   └─ SelfStudy      （仅留枚举位）   │
└─────────────────────────────────────┘
                ↓
        Scene 决定的差异：
          - 阶段切换 UI 是否显示（仅 KaoyanMath 显示）
          - 默认 retention（KaoyanMath: 90% / SelfStudy: 85%）
          - 是否需要"考试日期"字段
          - 内容包来源（不同 Scene 加载不同公式集）
          - 推送策略（一/二/三/冲刺只对 KaoyanMath 生效）
```

**当前阶段实现要求**：所有 Scene 相关行为通过 `UseScene` enum 分支判断，**禁止**直接读 `targetExamDate` 或 `SprintModeManager` 而不经 Scene 守卫。

### 3.2 五阶段闭环（编码 / 巩固 / 提取 / 辨析 / 迁移）

照搬研究报告 §3.1 五阶段模型，但映射到当前三 Tab：

| 阶段 | 当前位置 | 重构后位置 |
|------|---------|----------|
| 编码 Encoding | Memory > FormulaDetail（仅临摹） | Memory > FormulaDetail：拆块讲解 + 推导链 + 条件高亮 + worked example × 2 |
| 巩固 Consolidation | 无 | 学习后立即"识别 + 条件单填空"（同日触发） |
| 提取 Retrieval | Review（单一 cloze 卡型） | Review（C1/C2/C3 分难度档抽样） |
| 辨析 Discrimination | 无 | Review（C5 易混 A/B 辨析卡） |
| 迁移 Transfer | Test（仅默写） | Review C6 题型卡 + Test 限时默写 |

### 3.3 6 类卡片矩阵（报告 §8）

| ID | 类型 | 当前是否存在 | 出现阶段 |
|----|------|------------|---------|
| **C1** | 识别卡（公式名 → 公式 + **条件 + 用途**） | ❌ | 一轮起 |
| **C2** | 拆块 Cloze（按 weight 抽样多挖空） | ½（已有 cloze 但无加权） | 一轮起 |
| **C3** | 条件先行卡（2s 强制展示**条件 + 用途**） | ❌ | 一轮起 |
| **C4** | 推导卡（5s 推导挑战 + 自评三档） | ❌（数据有 `derivationSteps`） | 二轮起 |
| **C5** | 易混辨析卡（A/B 选） | ❌ | 二轮起 |
| **C6** | 题型应用卡（看例 → 反查） | ❌ | 一轮看例 / 二轮反查（**用户拍板分两阶**） |

每张子卡有独立 retrievability，但共享同一公式的"母 stability"——避免互相污染（FSRS 社区主流做法）。

### 3.4 加权 cloze 抽样（核心创新）

`clozeData` 升级：每个 `ClozeItem` 加 `weight: Int`（默认 1）+ `mustBlank: Boolean`（条件关键词必挖）。
抽样时按 `weight × (1 + 用户最近错次)` 加权随机，避免每次挖同样位置（Bjork variability）。

### 3.5 编码阶段七步学习仪式（用户拍板修订，2026-05-08；2026-05-19 扩为 7 步）

| 步 | 内容 | 关键点 |
|---|------|-------|
| 1 | **条件 + 用途先行卡**（2 秒强制展示） | **必须同时展示「适用条件」和「这公式干啥用」**，仅条件不够（用户修订） |
| 2 | 拆块讲解（可滑动） | — |
| 3 | 推导链静态展示 | 复用 `derivationSteps` 字段，不做动画 |
| 4 | 临摹手写 | 沿用既有 `TracingCanvas` |
| 5 | Worked Example × 2（看例，只读不做） | C6 一阶，novice 不做题（Sweller） |
| 6 | **最小填空预热** | **挖公式本体一处低权重位置**（用户修订：不再只挖条件关键词），确认理解再入 FSRS 队列 |
| 7 | **巩固迷你卡序列**（2026-05-19 新增） | **3 张同公式不同角度 mini-card：C1（题面→公式名）+ C2（公式挖空）+ C3（条件→公式名）混合**；错答记下，每轮做完后回头重做错的，**全 3 张通过才结业**。首次学习就让用户对公式形成全面理解，防止后续学习偏差认知。 |

**第 7 步设计依据**：
- **1A：固定 3 张 mini-card** —— 测试效应文献里"3 次正确检索"是普遍引用阈值；短小不疲劳
- **2B：C1+C2+C3 混合**（不是全 C2 同公式不同位置）—— 用户决策：首次编码必须多角度，单卡型容易形成片面记忆
- **3B：多轮次洗刷错答**（不是立即重做单题）—— 用户决策："多轮次洗刷更能让用户从短期记忆扩到长期记忆"。结业后 6 张子卡 `stability = 1.0, nextReviewTime = 次日刷新整点`
- **4A：结业后统一进次日 FSRS 池**，**APP 一整天不主动二次打扰用户**

### 3.5.1 产品哲学：APP 不做同日二次推送（2026-05-19 拍板）

**用户原话**：「用户在第一天学习完成后应不被二次打扰，10 分钟和 30 分钟这两个时间点实在影响用户体验，应该做到用户自发打开学习/添加错题和每天复习计划进行。不应在用户一天学完后还要再次进来学习。」

**含义**：
- APP 不打通知给用户"该巩固了"；也不靠 within-day 间隔队列
- **学习会话内必须自闭环**：编码仪式第 7 步的"3 张 mini-card + 错答多轮洗刷"承担同会话内的记忆强化
- 跨日复习走标准 FSRS（`SubCardStateEntity.nextReviewTime`）
- **唯一允许的主动提醒**：`DailyReminderWorker` 每日单次提醒"今日复习时间到"（属用户主动设置的计划，不算骚扰）

**新增字段**：`FormulaEntity.purpose: String`（一句话讲公式干啥用），供 C1/C3 卡片使用。

### 3.6 切断用户文字输入（贯穿原则）

| 场景 | 旧方案（已搁置或拟） | 新方案 |
|------|------------------|-------|
| 反馈错位 | 手输正确 LaTeX | placeholder chip 多选（已落地） |
| 学习自评 | 写"小错在哪" | 三档按钮（不会 / 小错 / 会） |
| 错题→公式反向 | 用户写"我这道题用错了 X 公式" | 从公式池**点选**错位公式 |
| FSRS 评分 | 自由文字反馈 | 4 档按钮（沿用现状） |
| 推导自评 | 写推导步骤 | 三档自评按钮 |

---

## 4. 关键方案对比

### 4.1 卡型推送策略（如何决定下一张卡是 C1/C2/.../C6？）

| 方案 | 利 | 弊 | 决策 |
|------|---|----|------|
| **A. 按 retrievability 自适应**（R<0.7 给 C1 / 0.7≤R<0.9 给 C2 / R≥0.9 给 C3-C6） | 自动适配难度 | 实现复杂、调试难 | ✅ **采纳**（报告 §5.1 主张） |
| B. 按用户进度阶段 | 简单 | 一刀切，不个性化 | 否决 |
| C. 用户手动选 | 高自由度 | 增加心智负担 | 否决 |

### 4.2 公式族图谱形态

| 方案 | 利 | 弊 | 决策 |
|------|---|----|------|
| **A. 图谱替代 Memory Tab 主入口** | 视觉冲击强、强化族关系 | 工程量极大、学习曲线陡 | ⏸️ Sprint 7 实施 |
| **B. 图谱作为章节内可选视图**（与列表共存） | 用户可选、风险低 | 双入口稍冗余 | ✅ **采纳** |
| C. 暂不做图谱 | 工程零 | 失去差异化 | 否决（核心创新点） |

### 4.3 阶段切换触发方式

| 方案 | 利 | 弊 | 决策 |
|------|---|----|------|
| A. 完全自动（按距考天数） | 用户无感 | 不灵活 | 否决 |
| B. 完全手动 | 用户控制 | 易遗忘切换 | 否决 |
| **C. 自动建议 + 用户确认** | 既准又灵活 | 多一次确认 | ✅ **采纳** |

阶段定义（`StudyPhase` enum）：`OneRound`（基础）/ `TwoRound`（强化）/ `ThreeRound`（真题）/ `Sprint`（冲刺）/ `Maintenance`（保持，考前一周）。仅 `KaoyanMath` Scene 启用。

### 4.4 错题反向链路入口

| 方案 | 利 | 弊 | 决策 |
|------|---|----|------|
| A. 独立"错题本"Tab | 完整 | 多一个 Tab，与现 4 Tab 冲突 | 否决 |
| **B. Memory Tab 内"错题本"二级页** | 不破坏底栏 | 入口稍隐 | ✅ **采纳** |
| C. 浮动按钮 | 醒目 | 视觉污染 | 否决 |

UI 流程：用户做完真题 → 打开错题本 → 新增条目（拍照 / 文字标题）→ 弹"哪些公式用错了？"→ 从公式池**多选**（chip 形态）→ 提交后被选公式 retrievability 强制下调到 0.5 + 加入次日队列。

---

## 5. 最终决策（用户已拍板，2026-05-08）

### 5.1 核心方向（5 项）

| # | 决策 | 状态 |
|---|------|------|
| **D1** | C6 题型卡分两阶：一轮起作为 worked example 看例；二轮起作为反查题让用户做 | ✅ |
| **D2** | 全面切断用户文字输入；所有交互走 chip / 按钮 / 多选 | ✅ |
| **D3** | Scene 三态架构（默认 `KaoyanMath` 做透 + 留 `Gaokao`/`SelfStudy` 枚举位 + 行为通过 enum 分支） | ✅ |
| **D4** | 升格为独立阶段「学习流程重构阶段（03）」，拆 4 个 Sprint | ✅ |
| **D5** | 内容工程：先 30 公式 MVP（人工标注），后期 ~300-500 公式用 AI 全标注 + 抽样人工 review | ✅ |

### 5.2 Sprint 1 阻塞细则（5 项，2026-05-08 拍板）

| # | 决策 | 状态 |
|---|------|------|
| **D6** | 错题本字段 = 来源 chip + 受限数字编号（subject/chapter/sourceType chip + sourceTag 受限数字）；无自由文字 | ✅ C |
| **D8** | 30 公式 MVP 范围 = 高数 20（极限基础 5 + 微分中值 5 + 泰勒族 10）+ 线代 5（特征值族）+ 概率 5（分布族）；按数一/二/三细分（详见 D11） | ✅ C |
| **D9** | `derivationSteps` 重写为对象数组：`[{latex: "...", note: "中文注释"}, ...]`；新建 `domain/model/DerivationStep.kt` | ✅ B |
| **D10** | 6 类子卡分别落库：新建 `SubCardStateEntity(formulaId + cardType 复合主键)`，每张子卡独立 S/D/R/lastReview/lapses；母 `StudyStateEntity` 保留作为公式整体进度展示 | ✅ A |
| **D11** | 数一/数二/数三 = 多对多关系表 `FormulaSubjectMap(formulaId, subjectType)`；`AppPreference.kaoyanSubject` enum；Onboarding 加"考数几"页（仅 KaoyanMath 显示） | ✅ C |

> 注：D7 编号已跳过（避免与历史版本冲突），后续 Sprint 阻塞决策从 D12 开始编号。

---

## 6. Scope 划定（明确不做什么）

### 6.1 不做（本阶段）

- ❌ **不重写 FSRS**：沿用 `ReviewScheduler` + DSR 三组件，仅在子卡 retrievability 派生上做扩展
- ❌ **不做 LaTeX 输入法**：已无限期搁置（改进点池❌已拒绝）
- ❌ **不做推导动画**：3Blue1Brown 风格逐帧动画工程量过大；先用静态推导链（`derivationSteps` 字段已有，做静态展示足够）
- ❌ **不做泛化场景**：仅留 enum 位，`Gaokao` / `SelfStudy` 的内容包/UI/默认值都不做
- ❌ **不做错题本拍照 OCR**：错题本仅文字标题 + 公式多选，拍照 OCR 工程量大且不属于核心闭环

### 6.2 推迟（本阶段不做但留扩展点）

- ⏸️ Khan Academy 4 级掌握度（Attempted/Familiar/Proficient/Mastered）—— 数据层留 enum，UI 留到下下阶段
- ⏸️ 周打卡 + 记忆持久度图（替代 streak）
- ⏸️ FSRS Optimizer（用户量积累后再做参数拟合）
- ⏸️ 公式 OCR 自动从教材生成 Formula Object

---

## 7. 反模式提醒（来自报告 §9，避免踩坑）

实施每个 Task 前对照检查：

1. ❌ 不要把"4 次连续答对"当作掌握判定 —— 数学公式必须 4 维度（识别/拆块/条件/题型）各一次
2. ❌ 不要把"4 选 1 选项题"作为主要复习手段 —— recognition ≠ recall
3. ❌ 不要让 novice 一上来就默写 —— 违反 worked example effect
4. ❌ 不要把整条长公式作为单一 cloze —— 必须拆块
5. ❌ 不要做"列出所有等价无穷小"这种 set 卡 —— 改逐条 cloze
6. ❌ 不要 Duolingo 式 streak 焦虑 —— 改周打卡 + 复习覆盖率
7. ❌ 不要让用户自评"我学会了"作为掌握判定 —— foresight bias，要靠测试结果
8. ❌ 不要照搬不背单词 listening / 真实语境例句 —— 公式没有那种语境
9. ❌ 不要忽略易混对 —— interference 是 SR 卡组失败常见原因
10. ❌ 不要让手写识别结果直接判对错 —— 5-10% 误判，必须用户自评辅助

---

## 8. 阶段拆分预览

详见 [`docs/phases/03_学习流程重构_TODO.md`](../phases/03_学习流程重构_TODO.md)。

> **Sprint 编号约定**：每阶段从 Sprint 1 重新计数（与原型阶段、打磨阶段一致），用户拍板 2026-05-08。

```
Sprint 1：数据基础（schema + Scene enum + 6 类卡 schema + 错题反向 schema）
       └─ 不动 UI，扩字段 + 升 DB + 30 公式 MVP 标注（内容工程 Track-A 启动）

Sprint 2：核心多卡型（C1/C2/C3）+ 复习路由器
       └─ Encoding 阶段六步学习仪式（条件+用途先行 → 拆块 → 推导 → 临摹 → 例题 → 公式本体最小填空）

Sprint 3：互动深化（C4 推导 + C5 易混 + C6 反查）
       └─ 错题反向链路 UI

Sprint 4：图谱 + 阶段切换
       └─ 公式族图谱（章节内视图）+ 一/二/三/冲刺切换 UI（仅 KaoyanMath）
```

**内容工程 Track**（独立于 Sprint，可并行）：
- Track-A：30 公式 MVP（手工，与 Sprint 1-2 同步）
- Track-B：300-500 公式 AI 全标注 pipeline 设计 + 抽样 review（Sprint 3 后启动）
- Track-C：易混对 + 推导链人工编辑工具
- Track-D：典型题题面 + worked example 收集

---

## 9. 二级决策点

### 9.1 Sprint 1 阻塞 ✅ 全部已答（2026-05-08）

详见 §5.2。所有 5 项决策（D6/D8/D9/D10/D11）均已拍板，Sprint 1 启动条件满足。

### 9.2 后续阻塞 ✅ 全部已答（2026-05-20）

- **D12 C5 易混对来源**（Sprint 3 阻塞）→ **B：复用 `confusableWith` 字段 + 新增 `diffExplanation`**
  - 30 公式 MVP 的 `FormulaEntity.confusableWith` 已含易混 formulaId 列表（Task 1.7 标注），Sprint 3 启动前补反向边 + 写差异说明文本
  - 数据落地形式：新建 `confusable_pairs` 表 `(idA, idB, diffExplanation)`（双向去重，idA < idB 字典序），或在 FormulaEntity 上直接挂 JSON——具体在 Sprint 3 Task 3.2 细化
  - 工作量预估：~3-4 小时写差异说明（30 公式 ≈ 10-15 对易混）

- **D13 C6 题面来源**（Sprint 3 阻塞）→ **C：教辅改编**
  - 参考 880 / 1800 / 武忠祥讲义等教辅题，关键改写 + 简化为「单条公式即可归因」版本
  - 规避真题版权 + 保留真实考研题感 + 单公式聚焦明确
  - 数据写入 `FormulaEntity.typicalProblems`（已存在的 JSON 字段），每公式 2-3 题
  - 内容工程 Track-D 负责：30 公式 × 2-3 题 ≈ 60-90 题，每题 10-15 分钟，总 15-20 小时，可与 Sprint 3 代码侧并行

- **D14 公式族图谱边类型**（Sprint 4 阻塞）→ **B：推导 + 易混（双色边）**
  - 节点：公式；边：黑/蓝色「推导关系」（来自 `parents`）+ 红/橙色「易混对」（来自 `confusableWith`）
  - 视觉密度可控（30 公式 ≈ 50-80 条边），既能看主轴又能看辨析点
  - "题型共享边"作为 Sprint 5+ 扩展项（数据规模上来后再加），记入改进点池

- **D15 学习阶段切换是否允许回退**（Sprint 4 阻塞）→ **A：严格单向 + 设置页"后悔药"**
  - 主路径：阶段一 → 二 → 三 → 冲刺 → 保持，**主 UI 不显示回退入口**
  - 后悔通道：设置页加「重置学习阶段」按钮（命名待定），**明确警告会重算所有公式 nextReviewTime**（FSRS retention 调整后批量重写）
  - 设计动机：主路径心智清晰，但仍留纠错口；防止误操作导致几百条 study_states 被重置
  - 实装注意：重置时需在 ReviewLog 写 `phase_reset` 类型记录，便于事后回溯

### 9.3 Sprint 2 启动决策 ✅ 已答（2026-05-20）

- **D-S2-1 Sprint 2 范围** → **B：全量 2.1-2.5**
  - 一次性闭环：七步学习仪式（首次激活 6 子卡）+ 复习路由器 + C1/C2/C3 三类卡型
  - 工期估算 8-12 天（叠加 D-S2-3 的子卡为准重写）

- **D-S2-2 复习路由器策略** → **D：轮转 + 粘卡 + 默写收尾**（用户原创设计，2026-05-20）
  - **轮询主循环**：每个公式持有 `cursor_F` 指向当前应考的子卡（按 c1→c2→c3→c4→c5→c6 顺序，跳过未 due）
  - **粘卡机制**：评分 ≥ 3 → cursor_F 推进到下一张 due 卡；评分 = 1 → cursor_F 不动，下一轮继续考同一张
  - **跨公式轮转**：每轮内逐个公式各考 1 张，循环到下一公式；不一次"考穷"单个公式的所有卡
  - **加强标记**：单卡 `round_lapses ≥ 3` → 标记"加强卡"，cursor_F 暂时跳过进入下一张；**全公式毕业时**回头再考一次
  - **默写收尾**：公式所有 due 卡均通过 → 进入默写阶段（手写完整公式，沿用 TracingCanvas / 纸笔自评 by InputMode）
    - 错 1 次：下次重做加 hint 1（露第一块）
    - 错 2 次：加 hint 2（露推导前两步）
    - 错 3 次：停止默写，记 `phase_status = blocked`，待用户主动回顾后重试
  - **会话结束条件**：所有公式均默写通过 / 标记 blocked
  - **cursor_F 持久化**：需建 `review_session_progress` 表保存中途进度（详情 Task 2.1 细化）
  - **未实装卡型 fallback**：Sprint 2 内 C4/C5/C6 UI 未做时，路由器跳过这些 cardType（不降级到 C1）

- **D-S2-2 补充：状态机细化（2026-05-20 二次拍板）**
  
  以下五条细化原则是 Task 2.1 的实现底稿，状态机绕不开：
  
  1. **默写形式：按 InputMode 自动二选一**
     - 沿用用户在 Settings 已选的 InputMode（TracingCanvas 手写 / 纸笔自评）。
     - 全程默写沿用同一模式，不每张卡当场再问，零打扰。
     - 切换 InputMode 走设置页，不在路由器内提供切换入口。
  
  2. **加强卡回考时机：该公式进默写前回考**（不是会话末尾统一回考）
     - 公式 A 的所有 due 卡都过了 → **先回考 A 的加强卡** → 才进 A 的默写。
     - 距离首次错题最近、生动；默写动机明确。
     - 回考评分 ≥ 3：加强卡标记清除（仅会话内）。
     - 回考评分 = 1：**升级为强标记**（见下文第 5 条）。
  
  3. **blocked（默写错 3 次）出口：下次复习"轮到默写时强提醒"**（2026-05-20 二次拍板订正）
     - **不跳队、不强行先做默写**——下次复习正常走轮转，blocked 公式的 due 卡照常考。
     - 当**轮到该公式的默写阶段**时，UI 顶部显示红色强提醒条：「上次默写被阻断，本次格外仔细」+ 重新从 hint 0 开始默写。
     - **手动重试**入口保留：FormulaDetail（信息展示页）顶部红色 banner + 按钮「再试一次」→ 跳回路由器进入该公式的默写。
     - 实现：`ReviewSessionProgressEntity` 持久化 `lastDictationBlocked` 标志；新会话从该字段构造 `FormulaContext.wasPreviouslyBlocked`，路由器把此标志透传到 `NextAction.StartDictation.wasPreviouslyBlocked` 供 UI 渲染。Graduated / 再次 Blocked 后由调用方清除。
  
  4. **跨会话恢复：同日 cursor 续上，跨日重开**
     - 用户中途退出复习 Tab → 进度持久化到 `ReviewSessionProgressEntity`。
     - 同一**自然日**内再进复习 Tab → 从 cursor_F 接着考、加强标记保留、回合数继续累计。
     - 跨日（次日及之后）再进复习 Tab → 视为新会话：重新拉 due 列表、cursor_F 重置、加强卡标记清零（强标记保留，因为强标记是跨会话的）。
     - "自然日"按设备本地 0 点切分；可结合 Settings 已有的"刷新点"配置（默认 4 AM）。
  
  5. **强标记（reinforcement flag）—— 加强卡的跨会话升级版**
     
     - **触发**：加强卡回考依然评 1 → 在 `SubCardStateEntity` 上置 `isReinforced = true`（或更精细的 `reinforcedAt: Long` 时间戳）。
     - **可见性**：FormulaDetail 信息展示页该子卡 chip 旁加 ⚠️ 标识或独立 banner，让用户感知到「这张子卡之前死磕都没过」。
     - **复习加强（双重）**：
       - **FSRS 层**：被打强标记时 `stability ×= 0.5`（沿用 `SprintModeManager.halveStabilityAbove` 的成熟交互，单独包一个 `applyReinforcement` 方法，避免和冲刺模式耦合）。
       - **路由器层**：同 due 时间下，强标记卡在每轮抽样中**优先排前**（先消化"老大难"再走新卡）。
     - **消除**：连续 3 次评分 ≥ 3 → 自动清除（在 `SubCardStateEntity` 加一个 `consecutiveGoodCount` 计数字段，评 1 时归零）。
     - **与 leech 的关系**：两套机制并存独立。leech 看 `lapses` 累积（≥ 4）；强标记看"加强卡回考再失败"这个特定行为。同时挂 leech + 强标记的卡 → UI 上两个标识都显示，路由器按强标记规则优先。

  > 三层标记总览：
  > | 标记 | 触发 | 范围 | 消除 | 复习加强 |
  > |---|---|---|---|---|
  > | 加强卡 | 会话内 round_lapses ≥ 3 | 当次会话 | 会话结束自动清 | 该公式默写前回考 |
  > | **强标记** | 回考再评 1 | 跨会话持久化 | 连续 3 次评 ≥ 3 自动清 | stability ×0.5 + 路由同 due 优先 |
  > | leech | 历史 lapses ≥ 4 | 跨会话累积 | （现有逻辑） | Memory Tab 红色染色 |

- **D-S2-3 母卡 vs 子卡 FSRS 数据写入** → **B：子卡为准，母卡 deprecated**
  - 用户评分时**只写 `sub_card_states`** 表；`study_states` 表保留但**不再由 ReviewViewModel 更新**
  - **派生策略**（Sprint 2 内必须重写）：
    - `learningState` → 6 子卡聚合：`MIN(stability) < 1.0 → 1（Learning）`；`AVG > 30 → 3（Mastered）`；其余 `2（Reviewing）`
    - `lapses` 累计 → `SUM(sub_cards.lapses)`
    - `nextReviewTime` → `MIN(sub_cards.nextReviewTime)`（最早到期的子卡决定推送时机）
    - `stability` → `AVG(sub_cards.stability)`（用于 Sprint Mode 判定）
  - **受影响代码**（必须 Sprint 2 内重写）：
    - `MemoryViewModel` 改读聚合 + `MemoryScreen` bucket 算法
    - `SprintModeManager.applyIfNeeded` 改操作 `sub_card_states`
    - `DailyReminderWorker` 改读 `MIN(sub_card_states.nextReviewTime)`
    - `TestViewModel` 测试模式评分写哪张子卡 → 默认 `c1`（识别），后续可由用户选
    - 现有 StudyStateDao 方法（`halveStabilityAbove` / `resetMasteredReviewTime`）废弃，等价方法迁到 `SubCardStateDao`
  - **风险预案**：Memory Tab 是核心 UI，重写时需附诊断角标 + 真机三轮验证

### 9.4 Sprint 4 启动决策 ✅ 已答（2026-07-09）

**背景**：Sprint 4 原定「公式族图谱 + 阶段切换」。开工前数据核查发现图谱数据稀疏（`parents` 9/30 · `confusableWith` 10/30，20/30 公式无边；漂亮的推导/易混链多为**跨章**），RFC 原定的「章节内视图」（D14 隐含）在现数据下 13/16 章是孤点，图谱会是空壳。溯源到**根因不是数据少，是关系被存成内嵌 JSON 单向边 + subject/chapter 是硬列**，无法表达"一条公式属于多个语境"。用户借此提出**产品愿景升级**：本 App 定位为**领域无关的记忆「外壳」**，总字典可由用户增删改（初高中数学 / 专业公式 / 甚至英语单词语法）。

- **D16 数据层地基标签化**（Sprint 4 阻塞）→ **全标签化分类 + 关系（方案乙），但只到分类/关系层**
  - **通用化边界（用户拍板）**：只把**分类层 + 关系层**标签化；`latexCode`/`clozeData`/`derivationSteps` 等**数学内容字段原样保留**，不做内容通用化（英语单词等别领域内容模型是将来的事）。**眼下仍以考研数学公式记忆核心功能为准，不偏移愿景**——地基只在「通用化 + 原子化」上铺好。
  - **原子化原则**：一条公式的"高数·微分中值定理·数一" = 3 条独立标签链，不塞复合字段；一条关系 = 一条带单一 type 的边；种子复合信息在加载时拆成原子行。
  - **新 schema**（destructive migration，v11→v12）：
    - `tags(tagId PK, namespace, value, displayName)`：namespace ∈ {subject, chapter, exam(数一二三), 未来任意维度}
    - `entry_tag_map(entryId, tagId, isPrimary)` 复合主键 + 双向外键：分类唯一真相源，承载 subject/chapter/exam/keyword 全部标签，**取代 `formula_subject_map`（数一二三 → namespace=exam）**。`isPrimary` 标主科目/主章节。
    - `entry_relations(fromId, toId, type)` 复合主键 + 双向外键：type ∈ {推导, 易混, 同族}，取代 `parents/siblings/confusableWith` 三个内嵌 JSON（反查/防悬空全解决）。
  - **实现取路径 2（务实版，2026-07-09 用户拍板）**：`tags` 表为分类唯一真相源；`FormulaEntity` 的 `subject`/`chapter` **保留为显示缓存**（仅种子期从主标签写一次，单写入者不漂移，供列表排序/`FormulaIndex` 分组/卡顶显示，避免重写核心 SQL 冒回归）；`tags` 自由关键词列保留（详情页 leech 横幅展示）。**只移除** `parents`/`siblings`/`confusableWith`（迁 `entry_relations`，全 App 零消费方）。路径 1（纯删列）与路径 2 扩展能力完全相同，差别仅"是否留显示缓存"，故取低风险的 2。
  - **D14 修订**：图谱边源从"读 JSON" 改为"读 `entry_relations` 表"。
  - **Task 4.1 已完成并真机验证（2026-07-09）**：3 表 + 3 DAO 建成，种子拆原子行，数一二三过滤 30/21/26 不变，364 单测绿，真机 smoke 过。
  - **阶段切换（原 4.1-4.3 StudyPhase）及其余未做项**：延后 Sprint 5，避免摊薄地基重构注意力。

- **D17 图谱呈现 + 交互**（Sprint 4 Task 4.2，2026-07-09 用户拍板）→ **图谱=记忆主视图，列表删除；语义缩放钻取**
  - **原 §4.2 决策 B（"章节内可选视图 + 与列表共存"）作废**：图谱升格为**记忆 Tab 的默认主视图**，承载外观 + 路由 + 学习状态展示；**旧列表删除**（不再作辅助模式）。
  - **布局**：母=**章节聚类分区**（章节块=固定空间方位=肌肉记忆锚点），子=**块内分层**（推导纵向）。经六种布局对比研究选定（力导向因违背"同池唯一/肌肉记忆"排除）。**确定性算法，同一公式池像素级唯一**。
  - **呈现 = 语义缩放钻取（两级）**：母层=章节气泡地图（气泡大小∝公式数、色=科目、外环=章节掌握度、虚线连跨章）；点气泡→开合动画钻入→子层=该章公式的块内分层子图。避免手机一屏塞 30 节点。
  - **交互**：母层拖动平移 + **松手吸附最近气泡**居中 + 底部当前气泡进度条；子层拖动不吸附；**点公式节点按状态路由**（未学→七步仪式 / 已学→详情页，沿用现有）；跨章关联用节点 **`↗N` 角标** → 点开列表 → 跳目标章并把目标公式吸到屏幕中心；返回=系统返回/捏合；气泡开合动画。
  - **渲染**：**Compose Canvas 画边 + Composable 节点 chip（显示公式中文简名）**；⚠ **禁止每节点塞 KaTeX/WebView**（30 WebView 拖死，打磨阶段已验），KaTeX 只在点开单个公式时渲染。节点状态 **3 态（未学灰虚线 / 学习中 / 已掌握绿）+ 顽固 🔥 红边叠加**（与全 App leech 视觉统一）。
  - **引擎原子化三层**：数据层（读 entry_relations+entry_tag_map→GraphModel）/ 布局层（`GraphLayout` 接口，可插拔，聚类+块内分层是首个实现）/ 渲染层（Canvas+Composable，布局无关）。以后换布局方式只改布局层。
  - **HTML 交互原型**（2026-07-09）验证了导航模型，概念通过；原型的 web 特有 bug（pointer capture 吞 click 等）不影响 Compose 实现。
  - **数据现实提醒**：现 30 公式下 13/16 章仅 1~2 条，子层偏稀疏——地基先行，满数据后才丰满（可选优化：单公式章节点气泡直接进学习）。

---

## 10. 验收标准（阶段全部 Sprint 完成后）

- ✅ `./gradlew.bat compileDebugKotlin testDebugUnitTest` BUILD SUCCESSFUL
- ✅ 真机 30 公式 MVP 跑通完整学习路径：编码 → 巩固 → 提取 → 辨析 → 迁移
- ✅ 6 类卡片在复习中按 retrievability 自适应抽样
- ✅ 错题本"我这道题用错 X 公式"勾选 → X 次日重现
- ✅ 公式族图谱可视化展示族关系 + 学习状态点亮
- ✅ KaoyanMath Scene 阶段切换 UI 可用；其他 Scene enum 留位但不显示阶段切换
- ✅ Sprint 4-7 总单元测试覆盖率不低于现 230 条 + 至少新增 80 条

---

## 附录 A：术语表

| 术语 | 含义 |
|------|------|
| Scene | 使用场景枚举（KaoyanMath / Gaokao / SelfStudy） |
| StudyPhase | 学习阶段（一轮 / 二轮 / 三轮 / 冲刺 / 保持） |
| Card Type | 卡片类型（C1-C6） |
| Formula Object | 公式作为知识对象的 9+ 字段集合（含 preconditions / parents / siblings / confusable / chunks / typical_problems / mnemonic 等） |
| Cloze Weight | clozeData 中每个 placeholder 的挖空权重 |
| Retrievability | FSRS 中的 R 组件，0-1，反映"现在能回忆起来的概率" |
| Mother Stability | 公式总 stability，所有子卡共享 |

## 附录 B：与原型阶段总结的索引差异

| 字段 | 原型阶段 | 重构后 |
|------|---------|-------|
| `FormulaEntity` | 9 字段 | 18+ 字段（加 **purpose** / preconditions / parents / siblings / confusableWith / typicalProblems / commonErrors / mnemonic / examWeight / scene） |
| `clozeData` | 平等 placeholder 列表 | 加权 + mustBlank 标记 |
| `derivationSteps` | 闲置 | C4 推导卡数据源 |
| 卡片类型 | 1（默写为主） | 6 |
| 学习阶段 | 1 个（仅 SprintMode） | 5 个（OneRound 起） |
| Scene | 无 | enum 三态 |

---

> **下一步**：用户审 RFC → 确认无大方向偏差 → 进入「学习流程重构阶段（03）」Sprint 1 启动。
> 启动 Sprint 1 前需回答 §9.1 的 4 个阻塞决策点（D6/D8/D9/D10）。
