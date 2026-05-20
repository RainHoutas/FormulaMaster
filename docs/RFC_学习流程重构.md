---
name: Formula Master 学习流程重构 RFC
description: 基于《考研数学公式学习—复习—考试全流程设计研究报告》的产品级重构方案
created: 2026-05-08
status: 已拍板（待动手实施）
related:
  - docs/考研数学公式学习—复习—考试全流程设计研究报告.md
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

详见 [`docs/phases/03_学习流程重构_TODO.md`](phases/03_学习流程重构_TODO.md)。

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
