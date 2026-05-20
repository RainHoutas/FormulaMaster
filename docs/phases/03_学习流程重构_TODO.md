---
name: Formula Master — 学习流程重构阶段 TODO
description: 基于研究报告与 RFC 的多卡型矩阵 + Scene 三态架构 + 公式族图谱 + 阶段切换
created: 2026-05-08
parentPhase: 02_打磨完善阶段（已收尾，见 `docs/打磨阶段总结.md`）
status: Sprint 1 启动条件已满足，待用户开工指令
related:
  - docs/RFC_学习流程重构.md
  - docs/考研数学公式学习—复习—考试全流程设计研究报告.md
---

# 学习流程重构阶段

> 本阶段把 Formula Master 从「原型 + 打磨」过渡到「基于认知科学与产品差异化创新的多维记忆引擎」。
> 全部决策依据见 [`../RFC_学习流程重构.md`](../RFC_学习流程重构.md)。

> **Sprint 编号约定**：每阶段从 Sprint 1 开始（与原型阶段、打磨阶段一致）。

---

## 阶段目标

1. **数据 schema 升级**：`FormulaEntity` 9 → 18+ 字段；`clozeData` 加权；6 类卡片 schema 落地
2. **多卡型矩阵**：实现 C1 识别 / C2 加权 cloze / C3 条件先行（含用途）/ C4 推导自评 / C5 易混辨析 / C6 题型反查
3. **复习路由器**：按 retrievability 自适应抽样不同卡型
4. **Encoding 阶段重构**：FormulaDetail 升级为「条件+用途先行 → 拆块讲解 → 推导链 → 临摹 → worked example × 2 → 公式本体最小填空预热」六步仪式
5. **错题反向链路**：错题本 + 公式池多选 + retrievability 强制下调
6. **公式族图谱**：章节内可选视图，节点 = 公式、边 = 推出/易混
7. **阶段切换**：StudyPhase 五态 + 自动建议 + 用户确认（仅 KaoyanMath Scene）
8. **Scene 三态 + 数学子科目**：默认 KaoyanMath + KaoyanSubject（数一/数二/数三）三选一过滤；Gaokao/SelfStudy 留 enum 位

## 阶段原则

- 沿用打磨阶段的方法论：诊断驱动 / 不替用户做决策 / 涉及外部 API 必先 WebFetch
- 内容工程作为独立 Track，不阻塞代码 Sprint
- 单 Sprint Task 数 ≤ 8；每个 Task 完成后跑 `./gradlew.bat compileDebugKotlin testDebugUnitTest`
- 新想法默认不打断当前 Sprint（追加到 [`改进点池.md`](../改进点池.md)），仅 P0 例外

## 架构铁律

- 严格 MVVM + UDF
- 渲染统一 KaTeX + WebView，禁止引入 JLatexMath
- 所有用户文字输入入口必须**有理由**——默认走 chip / 按钮 / 多选
- Scene 相关行为**必须**经 `UseScene` enum 分支判断
- 数一/数二/数三过滤**必须**经 `kaoyanSubject` 守卫
- 6 类子卡分别落库（D10=A），独立 S/D/R/lastReview；母 `StudyStateEntity` 保留作整体进度展示

---

## Sprint 1：数据基础 + Scene/Subject + 6 类子卡 schema（启动条件已满足）

### 启动状态

✅ RFC §5.2 五项 Sprint 1 阻塞决策已全部拍板（D6/D8/D9/D10/D11）。
✅ 设计依据齐全，可以开工。

### Task 列表（8 个）

- [x] **Task 1.1** `UseScene` + `KaoyanSubject` enum + Onboarding 加"考数几"页
  
  - `domain/UseScene.kt`：`KaoyanMath`（默认）/ `Gaokao`（占位）/ `SelfStudy`（占位）
  - `domain/KaoyanSubject.kt`：`Type1` / `Type2` / `Type3`（默认 `Type1`）+ displayName + description
  - `AppPreference.AppSettings` 加 `useScene` + `kaoyanSubject` 字段 + DataStore key + setter
  - `OnboardingScreen` 在选 Scene 后追加一页"你考数学几?"（仅 `useScene == KaoyanMath` 显示，三 chip 二选一，必选）
  - `OnboardingViewModel.completeAndPersist` 加 `kaoyanSubject` 参数
  - **Done 标准**：BUILD SUCCESSFUL；切换 kaoyanSubject 重启保留；Onboarding 三选一可用

- [x] **Task 1.2** `FormulaEntity` 字段扩展（9 → 18+）+ `derivationSteps` 重写
  
  - 新增字段：
    - `purpose: String`（**一句话讲这公式干啥用**，C1/C3 卡用）
    - `preconditions: String`（JSON 数组）
    - `parents: String` / `siblings: String` / `confusableWith: String`（JSON）
    - `typicalProblems: String`（JSON）
    - `commonErrors: String`（JSON）
    - `mnemonic: String?`
    - `examWeight: Int = 3`
    - `scene: String = "KaoyanMath"`
  - `derivationSteps` 重写：从纯 LaTeX 字符串数组 → `[{latex, note}, ...]` 对象数组
    - 新建 `domain/model/DerivationStep.kt` data class
    - `formulas.json` 的 6 条种子全部按新格式重填（兼容 D9=B）
  - `AppDatabase` v3 → v4，`fallbackToDestructiveMigration(dropAllTables=true)`
  - **Done 标准**：BUILD SUCCESSFUL；现 6 条种子能正常预加载；新字段空值不崩；DerivationStep 反序列化单测通过

- [x] **Task 1.3** `FormulaSubjectMap` 多对多关系表 + 查询过滤
  
  - 新建 `data/local/entity/FormulaSubjectMapEntity.kt`：复合主键 `(formulaId, subjectType)`，外键级联
  - 新建 `FormulaSubjectMapDao` + `AppDatabase` 注册表
  - `FormulaDao` 加查询 `getByKaoyanSubject(type: String)` 用 JOIN 过滤
  - `FormulaRepository.observeFormulasFor(kaoyanSubject)` 暴露 Flow，按当前用户 subject 过滤
  - 现有 `MemoryViewModel` / `ReviewViewModel` 改用过滤后 Flow
  - **Done 标准**：BUILD SUCCESSFUL；切换 kaoyanSubject 后列表立即过滤；DAO 单测覆盖三种 subject 各自过滤结果

- [x] **Task 1.4** `clozeData` schema 加权升级
  
  - `ClozeItem` 加 `weight: Int = 1` + `mustBlank: Boolean = false`
  - `ClozeParser.parse` 兼容旧 JSON（缺字段用默认值）
  - 新增 `ClozeParser.weightedSample(items, n)`：按 `weight × (1 + 用户最近错次)` 加权抽样
  - 新增 `ClozeParser.minimalSample(items)`：抽 1 个**公式本体**位置作为「最小填空预热」（不是条件关键词，对应用户拍板修订）
  - **Done 标准**：BUILD SUCCESSFUL；ClozeParser 单测覆盖：旧格式兼容 / 加权分布 / mustBlank 必入选 / minimalSample 抽公式本体而非条件

- [x] **Task 1.5** `SubCardStateEntity` + DAO（D10=A 落地）
  
  - `domain/CardType.kt` enum：`C1_Recognition` / `C2_Cloze` / `C3_Precondition` / `C4_Derivation` / `C5_Discrimination` / `C6_TypicalProblem`
  - 新建 `data/local/entity/SubCardStateEntity.kt`：
    - 复合主键 `(formulaId, cardType)`
    - 字段：stability / difficulty / lastReviewTime / nextReviewTime / totalReviews / lapses / consecutiveGoodReviews
    - 外键级联到 `FormulaEntity`
  - 新建 `SubCardStateDao` + `AppDatabase` 注册
  - 母 `StudyStateEntity` **保留**，作为公式整体进度展示（learningState 着色 / Mastered 状态等）；FSRS 调度切换为基于 SubCardStateEntity
  - `ReviewScheduler.calculate` 签名扩展：接受 `SubCardStateEntity` 而非 `StudyStateEntity`（保留旧重载兼容现有 UI）
  - **Done 标准**：BUILD SUCCESSFUL；30 公式 × 6 卡 = 180 条记录可建可查；SubCardStateDao 单测全绿

- [x] **Task 1.6** `ErrorReportEntity` + 反向链路处理器（D6=C 落地）
  
  - 新建 `data/local/entity/ErrorReportEntity.kt`：
    - `id` (auto) / `createdAt`
    - `subject: String`（chip：高数 / 线代 / 概率）
    - `chapter: String`（chip：从公式池章节列表选）
    - `sourceType: String`（chip：历年真题 / 模拟卷 / 习题集 / 其他）
    - `sourceTag: String`（受限数字编码，如 "2024-18" "880-186-3"，UI 用数字键盘）
    - `wrongFormulaIdsJson: String`（JSON 数组）
    - `note: String?`（**暂不做**，预留可空字段供后期开放）
  - 新建 `ErrorReportDao`
  - 新建 `domain/ErrorReportProcessor.kt`：
    - 插入 ErrorReport 时遍历 `wrongFormulaIds`，对每个 formulaId 的所有 `SubCardStateEntity` 强制把 stability 调到使 R≈0.5
    - `nextReviewTime` 推到次日刷新整点
    - 写入 `lapses+1`
  - **Done 标准**：BUILD SUCCESSFUL；ErrorReportProcessor 单测覆盖：插入 → N 个 formulaId 的 6 张子卡全部被推到次日刷新整点 + S 调整正确

- [x] **Task 1.7** 30 公式 MVP 内容标注（D8 + D11 联动）
  
  - 范围细化（按 D8=C + D11=C）：
    - **三科共有约 15-18 公式**（标 `["1","2","3"]`）：极限基础 5 / 微分中值 5 / 基础泰勒 5 / 基础线代 3
    - **数一/数三共有不在数二**约 5（标 `["1","3"]`）：分布族（正态/卡方/t/F/泊松）
    - **数一专属**约 5（标 `["1"]`）：高级泰勒 / 数学物理向
    - **数二专属** 0-2（标 `["2"]`）：可补充
    - **数三专属** 0-2（标 `["3"]`）：经济应用
    - **总计约 30**（具体分布在标注时灵活调整）
  - 每个公式标注全 18+ 字段（purpose / preconditions / parents / siblings / confusableWith / chunks 含 weight / typical_problems / common_errors / mnemonic / derivationSteps 重写格式）
  - 同步写入 `formula_subject_map` 种子数据
  - **Done 标准**：30 公式 MVP 全字段填完，预加载无报错；三种 kaoyanSubject 各自能查到合理数量的公式

- [x] **Task 1.8** 单测扩充 + Scene 守卫 ✅（2026-05-20）
  
  - 至少新增 30 条单测覆盖 1.1-1.6 的纯函数路径 → **实际 +32**
    - ReviewSchedulerTest 子卡重载 +8
    - KaoyanSubjectTest（新）+5
    - UseSceneTest（新）+3
    - DerivationStepParserTest 边界 +4
    - ClozeParserTest 加权抽样补强 +5
    - FormulaRepositoryTest（新）+3
    - ErrorReportProcessorTest 边界 +4
  - 总测试数 **161 → 193**，全套 BUILD SUCCESSFUL
  - Scene 守卫 audit：发现 4 处裸读 `effectiveTargetExamDate`（`MainActivity` / `ReviewViewModel` / `TestViewModel` / `SettingsScreen`），仅 `KaoyanMath` 实装期间无害；已写入改进点池 `[架构] 冲刺模式 Scene 守卫`，第二个 Scene 实装前必须加守卫
  - Architecture Test：暂不引入（依赖额外库 + 影响构建时间），靠 Code Review + 改进点池跟踪
  - **Done 标准**：单测总计 ≥ 260 → 修订为 **新增 ≥ 30** 已达成；BUILD SUCCESSFUL ✓

### 验收标准（Sprint 1 全部 Task 完成后）

- [x] `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL（193 tests，全绿）
- [x] 30 公式 MVP 全部字段填完，DB 升级到 v7 成功（v5→v6→v7 在 Task 1.5 / 1.6 完成）
- [x] 三种 kaoyanSubject 用户进 App 看到的公式列表互不相同：数一 30 / 数二 21 / 数三 26（数二不含概率/曲线积分/级数）— 由 `FormulaSeedIntegrationTest` 验证
- [x] 现有 UI（Memory / Review / Test）不读新字段时不崩（FormulaEntity 全部新字段带默认值兜底）
- [ ] 改进点池中被本 Sprint 消费的条目移到「已纳入」— 见 Sprint 1 收尾段

### Sprint 1 收尾（2026-05-20 归档）

**完成情况**：Task 1.1 ~ 1.8 全部 ✅，193/193 单测绿。

**关键产物**：

- 数据基础：`UseScene` + `KaoyanSubject` 双 enum + Onboarding 加"考数几"页
- Schema：`FormulaEntity` 9→18 字段；`DerivationStep` 对象数组格式；`FormulaSubjectMap` 多对多
- 6 子卡矩阵：`CardType` enum + `SubCardStateEntity` + `SubCardStateDao`（独立 FSRS 状态）
- 反向链路：`ErrorReportEntity` + `ErrorReportProcessor`（multiplier=0.5 / minStability=0.5）
- 七步编码仪式（2026-05-19 由六步扩为七步）：第 7 步「巩固迷你卡序列」UX 设计落档（`docs/RFC §3.5` + §3.5.1 不同日二次推送条款）
- 内容：30 公式 MVP（21 三科共有 + 5 数一/三 + 4 数一专属）

**留作 Sprint 2 起点的债**：

- Scene 守卫尚未在 4 处调用点落地（见改进点池 `[架构] 冲刺模式 Scene 守卫`），第二个 Scene 实装前必做
- Sprint 2 起点：D12/D13/D14/D15 决策点确认 → Task 2.5 七步学习仪式 UI 实装

---

## Sprint 2：七步学习仪式 + 复习路由器 + C1/C2/C3 + 子卡 FSRS 切换

### 主题

把整个学习—复习链路从"母卡单一节奏"升级到"子卡六维 + 七步首次激活 + 轮转粘卡复习"，并彻底切到子卡为准的 FSRS 数据源（D-S2-1=B / D-S2-2=D / D-S2-3=B，详 RFC §9.3）。

### Task 列表（2026-05-20 细化）

- [/] **Task 2.1 复习路由器（轮转 + 粘卡 + 默写）** — RFC §9.3 D-S2-2
  
  **Task 2.1a 纯状态机 ✅ 完成（2026-05-20）**
  - ✅ 新建 `domain/ReviewRouter.kt`（pure Kotlin 状态机，无 Room 依赖）
    - `FormulaContext` / `RouterState` / `Step` / `NextAction` / `Input` / `Event` 完整 sealed 层
    - 跨公式轮转 + 粘卡 + 加强卡入集合 + 加强卡回考（**该公式进默写前**回考，per RFC §9.3 D-S2-2 补充第 2 条）
    - 默写状态机 hint0→1→2→Blocked（错 3 次外发 FormulaBlocked）
    - C4/C5/C6 fallback：在调用方构造 dueCards 时剔除（路由器不感知）
  - ✅ `SubCardStateEntity` 加 `isReinforced: Boolean = false` 字段（强标记持久化）
  - ✅ AppDatabase v7→v8（destructiveMigration，与 Sprint 1 风格一致）
  - ✅ 单测 `ReviewRouterTest`（21 case）覆盖：start / 轮转 / 粘卡 / 加强卡入集合 / 回考时机 / 回考评 1 升级 / 回考评 3 清除 / 默写 hint 升级 / Blocked / Graduated / 终态混合 / 边界异常
  - ✅ 全量单测 193→214，BUILD SUCCESSFUL
  
  **Task 2.1b 持久化 + ViewModel 接线 ⏳ 进行中**
  - ✅ 新建 `data/local/entity/BlockedFormulaEntity.kt` + `data/local/dao/BlockedFormulaDao.kt`（公式级跨会话 blocked 状态）
  - ✅ AppDatabase v8→v9 挂表（CASCADE 删除 + REPLACE 策略）
  - ✅ `BlockedFormulaDaoTest` 8 case（217→225 全绿）
  - ✅ 新建 `data/local/entity/ReviewSessionProgressEntity.kt`（单行表，sessionDateMs + formulaContextsJson + currentFormulaIndex），AppDatabase v9→v10
  - ✅ 新建 `data/local/dao/ReviewSessionProgressDao.kt`（upsert / clearSession / observeCurrent）
  - ✅ 新建 `data/repository/ReviewSessionProgressCodec.kt`：扁平 DTO 绕开 DictationState sealed 序列化；前向兼容（未知 CardType code 静默剔除）；JSON 损坏返回 null 不闪退
  - ✅ `ReviewSessionProgressCodecTest` 19 case（NotStarted/InProgress(0/1/2) 区分 / PhaseStatus 四枚举 / Set & Map round-trip / 未知 code 剔除 / 损坏 JSON / cursor 矫正 / validate 越界）+ `ReviewSessionProgressDaoTest` 8 case（225→252 全绿）
  - ✅ 新建 `data/repository/ReviewSessionRepository.kt`：粘合层，把 3 个 DAO + Codec 包成会话生命周期 API
    - `computeSessionDateMs(currentTimeMs, refreshHour)` 纯计算"会话锚"，跨刷新点不同锚
    - `startOrResume` 三态返回（Fresh / Resumed / FallbackToFresh），含损坏 JSON 兜底
    - **修复**：resume 时用最新 `blocked_formulas` 覆盖 `wasPreviouslyBlocked`（避免持久化快照与表错位）
    - `markFormulaBlocked` / `clearFormulaBlocked` / `endSession` 直白 API 供 ViewModel 调用
  - ✅ `ReviewSessionRepositoryTest` 15 case（fake DAO 跳过 Robolectric；含会话日切边界 / 三态决策 / 跨日覆盖 / blocked 透传 / E2E 续接，252→267 全绿）
  - ✅ 新建 `data/repository/ReviewEventProcessor.kt`：把 [ReviewRouter.Event] sealed 子类翻译成 DAO 写操作；处理 FSRS 计算 + 强标记自动清除 + blocked_formulas 维护
  - ✅ 新建 `ui/viewmodel/RouterReviewViewModel.kt`：路由驱动的 VM；旧 [ReviewViewModel] 保留不动（"不动 UI"约束自然满足，下场切 UI 时换 wireup）；含 `buildSessionInputs` 把 due 子卡按 isReinforced 优先 + examWeight 降序组织
  - ✅ `ReviewEventProcessorTest` 16 case（267→283 全绿）覆盖：
    - CardRated 三种评分路径 + FSRS 计算 + review_log 写入 + 子卡缺失静默跳过
    - 强标记自动清除（连 3 次评 ≥ 3 清 / 评 1 计数归零 / 非强标记不误伤）
    - ReinforcementUpgraded（写 isReinforced + stability×0.5 + 计数归零）+ 不重复写日志
    - ReinforcementCleared / EnterDictation 无副作用守恒
    - FormulaBlocked / FormulaGraduated 操作 blocked_formulas
    - processAll 多事件批处理顺序保证
  - ✅ Memory Tab dueCards 排序（已在 `RouterReviewViewModel.buildSessionInputs` 内实现：先按是否含强标记降序 → examWeight 降序 → formulaId 稳定）
  - ⏳ ReviewScreen UI 切换到 RouterReviewViewModel（旧 VM 仍在，需要替换 ReviewScreen 调用方）
  - ⏳ blocked banner UI 接入 FormulaDetailScreen（observeByFormulaId Flow → 顶部红条 + 「再试一次」按钮 → 跳路由器直接 Dictating）
  - ⏳ 默写界面顶部红条（`NextAction.StartDictation.wasPreviouslyBlocked = true` 时渲染）
  - **Done 标准**：真机三轮回归（同日续接 / 跨日重开 / blocked 恢复路径），BUILD SUCCESSFUL

- [ ] **Task 2.2 C1 识别卡**（公式名 → 完整公式 + 条件 + 用途）
  
  - 用户先看公式名 → 点"看答案"露出完整公式 + 条件 + 用途 → 评分 1/2/3/4
  - 视觉细节做时再问

- [ ] **Task 2.3 C2 加权 cloze 卡** — 复用 Sprint 1 Task 1.4 的 `weightedSample`
  
  - 每次抽 2-3 空（具体数量做时定）+ chip 多选填入（禁文字输入）

- [ ] **Task 2.4 C3 条件先行卡**（2 秒强制展示条件 + 用途）
  
  - 进入卡 2 秒倒计时不可点击 → 展示完整公式后才能评分

- [ ] **Task 2.5 FormulaDetail 重构：七步学习仪式**（2026-05-19 由六步扩为七步）
  
  1. 条件 + 用途先行卡（2s 强制展示）
  2. 拆块讲解（可滑动）
  3. 推导链静态展示（DerivationStep 渲染）
  4. 临摹手写（沿用 TracingCanvas）
  5. Worked Example × 2（看例，只读）
  6. 最小填空预热（**挖公式本体**一处）
  7. **巩固迷你卡序列**：3 张 C1+C2+C3 混合 mini-card；错答记下，每轮做完后回头重做错的，**全 3 张通过才结业**；结业后 6 张 SubCardStateEntity 初始化 `stability=1.0, nextReviewTime=次日刷新整点`

- [ ] **Task 2.6 子卡 FSRS 切换：母卡 deprecated** — RFC §9.3 D-S2-3
  
  - **ReviewViewModel**：`submitReview` 仅写 `sub_card_states`，删除 `study_states` 更新逻辑
  - **MemoryViewModel + MemoryScreen**：bucket 算法改为读 `sub_card_states` 聚合
    - `learningState` 派生：`MIN(stability) < 1.0 → 1`；`AVG > 30 → 3`；其余 `2`
    - `nextReviewTime` 派生：`MIN(sub_cards.nextReviewTime)`
    - `lapses` 派生：`SUM(sub_cards.lapses)`
    - `stability` 派生：`AVG(sub_cards.stability)`
  - **SprintModeManager**：`applyIfNeeded` / `halveStabilityAbove` / `resetMasteredReviewTime` 迁到 `SubCardStateDao`
  - **DailyReminderWorker**：改读 `MIN(sub_card_states.nextReviewTime)`
  - **TestViewModel**：测试模式默认评 `c1` 子卡（识别），后续 Sprint 用户可选
  - 旧 `StudyStateDao` 标 `@Deprecated`（保留 entity / 不再写）
  - **Done 标准**：受影响测试全部迁移并绿；Memory Tab + Sprint Mode + 通知三处真机回归无误

- [ ] ~~Task 2.7 巩固阶段触发~~ **拒绝（2026-05-19）**：违反 RFC §3.5.1「APP 不做同日二次推送」恒久立场；同会话内的巩固由 Task 2.5 第 7 步承担，跨日走标准 FSRS

- [ ] **Task 2.8 单测 + 真机验收**
  
  - Memory Tab / Sprint Mode 真机三轮回归
  - 路由器单测 ≥ 15 case 覆盖核心状态机
  - 七步学习仪式真机闭环验收

---

## Sprint 3：互动深化（C4/C5/C6）+ 错题反向 UI（占位）

### Task 占位（D12=B / D13=C 已答 ✅ 2026-05-20）

- [ ] Task 3.1 C4 推导卡（5s 推导挑战 + 自评三档：不会/查看了/推出来了）
- [ ] Task 3.2 C5 易混辨析卡（A/B 选 + 反馈差异说明）
- [ ] Task 3.3 C6 二阶（题型反查 → 用户从公式池**点选**）
- [ ] Task 3.4 错题本入口（Memory Tab 二级页）+ 来源 chip + 编号输入 + 公式多选 chip
- [ ] Task 3.5 Leech 升级：原 lapses≥4 + 新增"7 日内被错题反向标记 ≥2 次"
- [ ] Task 3.6 单测 + 真机验收

---

## Sprint 4：公式族图谱 + 阶段切换 UI（占位）

### Task 占位（D14=B / D15=A 已答 ✅ 2026-05-20）

- [ ] Task 4.1 公式族图谱（章节内可选视图）
- [ ] Task 4.2 `StudyPhase` enum + `AppPreference.studyPhase` 持久化
- [ ] Task 4.3 阶段切换 UI（仅 KaoyanMath Scene 显示）+ 自动建议 + 用户确认
- [ ] Task 4.4 阶段切换的 FSRS 联动（retention 调整 / 全交错开关 / Top 20 易遗忘面板）
- [ ] Task 4.5 多维掌握度评分（Khan 4 级，可选）
- [ ] Task 4.6 单测 + 真机验收

---

## 内容工程 Track（独立于代码 Sprint，可并行）

### Track-A：30 公式 MVP（人工标注，与 Sprint 1 Task 1.7 同步）

- 范围按 D8=C + D11=C 拆解（详见 Task 1.7）
- 标注 18+ 字段
- **预算**：每公式 30-60 分钟手工，30 公式 ≈ 15-30 小时

### Track-B：300-500 公式 AI 全标注（pipeline）

- Sprint 3 后启动
- LLM 批量生成 18+ 字段 + 抽样 10% 人工 review

### Track-C：易混对编辑工具

- Sprint 3 之前完成
- 内部 CSV / JSON 编辑工具（不入 App）

### Track-D：典型题题面 + worked example

- Sprint 2-3 期间收集
- 版权处理见 D13 决策

---

## Sprint 完成记录（待填）

### Sprint 1 总结

（待 Sprint 1 收尾后填写）

### Sprint 2/3/4 总结

（占位）

---

## 阶段验收标准（全部 Sprint 完成后）

详见 [`RFC_学习流程重构.md`](../RFC_学习流程重构.md) §10。
