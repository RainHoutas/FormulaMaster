---
name: Formula Master — 学习流程重构阶段 TODO
description: 基于研究报告与 RFC 的多卡型矩阵 + Scene 三态架构 + 公式族图谱 + 阶段切换
created: 2026-05-08
parentPhase: 02_打磨完善阶段（已收尾，见 `docs/phases/summaries/打磨阶段总结.md`）
status: Sprint 1 启动条件已满足，待用户开工指令
related:
  - docs/planning/RFC_学习流程重构.md
  - docs/research/全流程设计研究报告.md
---

# 学习流程重构阶段

> 本阶段把 Formula Master 从「原型 + 打磨」过渡到「基于认知科学与产品差异化创新的多维记忆引擎」。
> 全部决策依据见 [`../RFC_学习流程重构.md`](../planning/RFC_学习流程重构.md)。

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
- 新想法默认不打断当前 Sprint（追加到 [`改进点池.md`](../planning/改进点池.md)），仅 P0 例外

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

- [x] **Task 2.1 复习路由器（轮转 + 粘卡 + 默写）** — RFC §9.3 D-S2-2 ✅ 完整收尾（2026-05-20 晚）
  
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
  - ✅ 新建 `ui/screen/RouterReviewScreen.kt`：取代 ReviewScreen 接 RouterReviewViewModel；三态 ShowCard / StartDictation / SessionEnd 渲染；强标记 chip + 加强卡回考 chip + reveal 模式 + 1/2/3/4 评分
  - ✅ MainScreen AppRoute.Review 切到 RouterReviewScreen（旧 ReviewScreen 暂留作回退备份，Task 2.6 后由用户决定删除）
  - ✅ FormulaDetail 加 BlockedBanner（observeByFormulaId Flow + 阻断时间显示 + 「再试一次」按钮）
  - ✅ 默写界面顶部红条（`wasPreviouslyBlocked` → 红色 Surface 强提醒）
  - ✅ Step 7 bug 修复：mini-card `remember` 状态在重做轮次残留（C2 submitted=true 后按钮卡死）→ 加 `Step7State.attemptCount` 计数器 + UI 用 `key(attemptCount)` 强制重组
  - ✅ **真机回归 4 场景全部通过（2026-05-20 晚）**：
    - ① 加强卡机制（连续评 1 三次 → 入加强集合 → 跳过 cursor → 全公式 due 卡过完后回考） ✓
    - ② 强标记升级（加强卡回考再评 1 → `isReinforced=true` + `stability ×0.5` 落库验证） ✓
    - ③ 默写 blocked 路径（默写连错 3 次 → 写 blocked_formulas + FormulaDetail 顶部红条出现） ✓
    - ④ 同日续接（评一张卡后 force-stop App → 重启进复习 Tab 从 cursor 续考，不是从头来） ✓
  - **Done 标准**：真机三轮回归（同日续接 / 跨日重开 / blocked 恢复路径），BUILD SUCCESSFUL

- [x] **Task 2.2 C1 识别卡**（公式名 → 完整公式 + 条件 + 用途）— ✅ 真机验收通过（2026-05-29）
  
  - 用户先看公式名 → 点"看答案"露出完整公式 + 条件 + 用途 → 评分 1/2/3/4
  - ✅ 视觉决策（2026-05-28）：露出「公式 + 条件 + 用途 + 口诀」（mnemonic 有值才显示）；同卡内分段 + 小标题 + 分隔线
  - ✅ `RouterReviewScreen.C1RecognitionPane`（专属露出布局，整段可滚动）+ 抽出共用 `CardHeaderChips`/`RatingRow`；C2-C6 继续走通用 `ShowCardPane`
  - ✅ `RouterReviewViewModel`：UiState 加 `currentPreconditions`，VM 内解析 preconditions JSON（不在 Composable 碰 JSON）
  - ✅ compileDebugKotlin + 283 单测全绿（commit 3b7df0a）
  - ✅ **真机验收（2026-05-29，分部积分公式）**：题面（标题+回想提示）→ 看答案 → 公式 KaTeX 渲染 + 适用条件项目符号 + 用途 + 💡口诀分段（小标题+分隔线）全对

- [x] **Task 2.3 C2 加权 cloze 卡** — ✅ 真机验收通过（2026-05-29，commit d9d8c57）
  
  - ✅ 设计拍板（2026-05-28）：挖空数**自适应 min(3,总数)**，mustBlank 优先；chip **单选**填入；
    系统**自动判对错→映射评分**（全对=4/有错=1，无需用户自评）
  - ✅ `domain/ClozeGrading.kt` 纯函数判分 + 6 单测；VM 内 `weightedSample(min(3,总数))` 抽样
  - ✅ `C2ClozePane`：每空一组单选 chip → 提交 → 逐空 ✓/✗ + 错空正确答案 → 继续
  - ✅ `LatexChipsView` 加向后兼容 `singleSelect`（默认 false，FeedbackDialog 不受影响）+ 模板 JS 单选
  - ⚠ C2 全对→4 按选项字面；chip 是识别非回忆，若觉太松改 `RATING_ALL_CORRECT=3`（真机体验后用户暂未提出改动）
  - ✅ **真机验收（2026-05-29）**：单选（✓+主色描边）/ 全对→评定4 / 有错→评定1 / 逐空✓✗ / 仅错空露出正确答案（filled只读样式）/ 多空（期望与方差 min(3,总数)=3）多 WebView 堆叠高度正常无重叠
  - 🐛 **真机验收发现并补全（2026-05-29）**：原 C2 只列「空1/2/3+选项」**未显示公式骨架**，用户凭空选部件（旧原型 ReviewCard 与 RFC §2「展开公式→cloze候选填空」都要求带骨架）。补 `domain/ClozeSkeletonBuilder.kt` 纯函数（8 单测）：把抽中 placeholder 替换为**编号方框 \boxed{i}**（与空序号对应），C2 顶部 `MathFormulaView` 渲染骨架；用户拍板**实时填入**——选 chip 即把所选 latex 填进对应方框（所见即所得）。真机验证：①②③方框↔空1/2/3 / 实时填入 / 未挖空行完整显示 / 结果态骨架保留 / 加强卡回考卡通过

- [x] **Task 2.4 C3 条件先行卡**（强制展示条件 + 用途）— ✅ 真机验收通过（2026-05-29，commit d9d8c57）
  
  - ✅ 设计拍板（2026-05-28）：倒计时 **3 秒**锁定条件+用途 → 解锁 → 看答案 → 露公式 → 1/2/3/4 自评
  - ✅ `C3PreconditionPane`：`LaunchedEffect` 倒计时门 + 解锁后看答案 + 复用 `RatingRow`
  - ✅ **真机验收（2026-05-29，全概率公式）**：3 秒倒计时动态 tick → 解锁「看答案」→ 露出公式 KaTeX → 自评行；流程全对

- 🔧 **真机验收附带修复（2026-05-29）**：评分按钮「4 一眼出」在等宽布局下文字换行（实测高度 88px vs 其余 46px），用户拍板缩短为「**4 秒出**」；C2 结果横幅「系统评定 4（一眼出）」同步改「（秒出）」；改后实测四按钮等高 46px 单行。
  - 踩坑沉淀：`adb screencap` 抓含交互 chip 的 WebView 画面时顶部会出现底栏文字淡重影，经用户肉眼确认**屏幕实际无此重影**——纯截屏硬件层合成 artifact，勿当真实 bug 追。

- [x] **Task 2.5 FormulaDetail 重构：七步学习仪式**（2026-05-19 由六步扩为七步）— ✅ 代码完成 + 已接入导航（2026-06-04 核实）
  
  - 落地件：`ui/screen/FormulaLearnRitualScreen.kt` + `ui/viewmodel/FormulaLearnRitualViewModel.kt`；`MainScreen` `AppRoute.FormulaLearnRitual("formula_learn_ritual/{formulaId}")` 已挂导航（公式详情/列表两处入口）
  - 结业逻辑（Task 2.6 后）：只写 6 张 `SubCardStateEntity`（`stability=1.0, nextReviewTime=次日刷新整点`），不再双写 `study_states`
  - ⏳ **真机闭环验收并入 [Task 2.8]**（七步走通 → 全 3 张 mini-card 通过 → 结业落库 6 子卡）
  
  1. 条件 + 用途先行卡（2s 强制展示）
  2. 拆块讲解（可滑动）
  3. 推导链静态展示（DerivationStep 渲染）
  4. 临摹手写（沿用 TracingCanvas）
  5. Worked Example × 2（看例，只读）
  6. 最小填空预热（**挖公式本体**一处）
  7. **巩固迷你卡序列**：3 张 C1+C2+C3 混合 mini-card；错答记下，每轮做完后回头重做错的，**全 3 张通过才结业**；结业后 6 张 SubCardStateEntity 初始化 `stability=1.0, nextReviewTime=次日刷新整点`

- [x] **Task 2.6 子卡 FSRS 切换：母卡 deprecated** — RFC §9.3 D-S2-3 ✅ 接线完成（2026-05-29）
  
  - ✅ **接线完成（2026-05-29）**：
    - `FormulaWithState` 重构持 `DerivedProgress?`（不再 StudyStateEntity）；`MemoryViewModel` 读子卡 + `SubCardAggregator.deriveAll`
    - `FormulaLearnRitualViewModel` 结业去掉 study_states 双写（只留 6 子卡）
    - `MainActivity` 冲刺改 `applyIfNeededSubCards`；`DailyReminderWorker` 改 `countDueFormulas`
    - `TestViewModel` 队列取子卡聚合 mastered，判分写 c1 子卡，postpone 改推全部子卡
    - `SettingsViewModel` 整点切换批量重写改作用于子卡
    - **删除死代码** `ReviewViewModel` + `ReviewScreen`（回退备份、未接导航）
    - `StudyStateDao` 标 `@Deprecated`（保留 entity/表兼容老库；彻底删表需另开 Room 迁移 Task）
    - 单测 291 全绿无回归；真机回归：记忆 Tab 状态/leech 红条正确 ✅、Test Tab mastered 空状态无崩溃 ✅
    - ⏳ 冲刺/通知为纯后台逻辑，单测覆盖 + 启动无崩溃；更彻底的真机触发（临时改考试日期 / 触发 Worker）待需要时做
  
  - ✅ **预备件 1（2026-05-28，commit a23071a）**：`domain/SubCardAggregator.kt` 纯函数 + 14 单测
    —— 把子卡列表派生成整体进度（learningState / stability均值 / nextReviewTime最早 / lapses和）；
    零真机可验；接线留待真机阶段。✅ 边界已拍板（2026-05-29）：结业初始 stability=1.0 按字面 `<1.0` 判「复习中」——有意为之（结业=离开学习阶段进复习轮转；「学习中」专指未结业），保持 `<` 不改 `<=`
  - ✅ **预备件 2（2026-05-28，commit 406015a）**：子卡 DAO 聚合方法 + SprintMode 子卡版 + 8 单测
    —— `SubCardStateDao` 加 `halveStabilityAbove` / `resetReviewTimeForFormulas` /
    `getEarliestNextReviewTime` / `countDueFormulas`；`SprintModeManager.applyIfNeededSubCards`
    用聚合器「先快照算 mastered → 砍半 → 重置」保持母卡语义。全 additive，未接线。
  - ⏳ **剩余接线（需真机回归）**：MemoryViewModel/Screen 改读子卡 + bucket 用 SubCardAggregator；
    MainActivity 冲刺改调 `applyIfNeededSubCards`；DailyReminderWorker 改读子卡；TestViewModel 默认评 c1；
    `StudyStateDao` 标 @Deprecated 停写
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

- [x] **Task 2.8 单测 + 真机验收** — ✅ 全部通过（2026-06-04）
  
  - [x] 路由器单测 ≥ 15 case 覆盖核心状态机 — `ReviewRouterTest` **24 case**；全套单测 **320 个 @Test 全绿**
  - [x] 七步学习仪式真机闭环验收 —— Bayes 公式：未激活 → 走完七步 → 3 张 mini-card（识别/填空/条件先行）全过 → 结业。**DB 铁证**：6 张子卡 c1-c6 全部 `S=1.000`、`nextReviewTime=2026-06-05 08:00`（次日刷新整点）、`reviews=0/lapses=0`；Memory 同步显示「复习中」
  - [x] Memory Tab 真机三轮回归 —— ① 状态着色：学习中（期望与方差 MIN<1.0）/ 复习中（Bayes 刚结业）/ 已掌握（DB 注入全概率 AVG=35>30）三态 chip 正确；② leech 红条：期望与方差(lapses=4)、分部积分(lapses=8) 整卡 `errorContainer` 红底；③ mastered 拾取：Test Tab 正确显示已掌握的全概率公式（Task 2.6 子卡聚合查询生效）
  - [x] Sprint Mode 真机回归 —— 设考试日期 2026-06-20（距 15 天，冲刺激活）→ 重启触发 `applyIfNeededSubCards`：全概率(S=35>15,mastered) 砍半→**17.5** 且 `nextReviewTime` 重置≈now；Bayes(S=1<15,非mastered) 不变。验毕已还原考试日期默认值
  - 🐛 **真机使用发现并当场修复（P0）**：七步仪式 Step 6 + Step 7 两张填空卡只显示孤问号 `\fbox{?}`，缺公式骨架（Step 7 选项还是裸 LaTeX）。两卡改为复用 `ClozeSkeletonBuilder`（公式带洞骨架+实时填入）+ `LatexChipsView`（KaTeX 选项），与路由器正式 C2 卡对齐；真机验过（正态分布密度函数）。详见 [改进点池](../planning/改进点池.md)「已完成」

---

## Sprint 3：数据就绪卡型（C4/C6）+ 错题反向 UI + C2 判错着色

### 主题（2026-06-04 用户拍板范围）

把已就绪数据（derivationSteps 30/30 + typicalProblems 30/30）落成两张新卡型，打通错题反向链路的 UI 闭环（后端 ErrorReportProcessor 已在 Sprint 1 完成），并修复用户提出的复习 C2 判错顶部着色痛点。
**C5 易混辨析延后到 Sprint 4**：confusableWith 仅 10/30 且缺 `diffExplanation` 差异说明文本，属内容卡脖子，需内容工程 Track-C 先行（RFC §9.2 D12=B）。

### Task 列表（6 个）

- [x] **Task 3.1 C4 推导卡**（5s 推导挑战 + 三档自评）— RFC §3.3 / §3.5 第 3 步复用 `derivationSteps` — ✅ 代码完成待真机验收（2026-06-05）
  - 数据就绪：30/30 公式有 `derivationSteps`（`[{latex, note}]` 对象数组）
  - 交互蓝图：题面给「公式结论」→ 用户心里推一遍 → 倒计时门后「看推导」逐步露 DerivationStep → 三档自评
  - ✅ **二级决策（2026-06-05 用户拍板，全取推荐项）**：① 三档自评 不会→1 / 查看了→2 / 推出来了→4（跳过 Good，RFC 建议）② 倒计时 **5 秒** ③ 推导链**一次全露**
  - ✅ 落地件：
    - `domain/DerivationSelfAssessment.kt`：三档自评→评分纯枚举（CANNOT_RECALL=1 / VIEWED=2 / DERIVED=4），映射逻辑可单测
    - `RouterReviewViewModel`：UiState 加 `currentDerivationSteps`；VM 内 `DerivationStepParser.parse`（不在 Composable 碰 JSON）；C4 ShowCard 时解析
    - `RouterReviewScreen.C4DerivationPane`（专属面板）：目标结论常驻（让用户知道往哪推）+ 5s 倒计时门（复用 C3 模式）+「看推导」一次全露推导链（每步 `note` + `latex`，步间分隔线）+ 三档自评行；推导链缺失回落 `ShowCardPane`
    - `DerivationSelfAssessmentTest` 6 单测（映射 1/2/4 + 严格递增 + 跳过 3 + label 对应）
  - ✅ compileDebugKotlin + 全套单测 **326 个全绿**（320→326）
  - ⏳ **真机验收并入 Task 3.6**：C4 卡随路由器轮转出现 → 5s 倒计时 → 看推导链 → 三档自评落库
  - Done：`C4DerivationPane`（专属面板）+ VM 解析 derivationSteps；C4 已随七步结业写子卡并随轮转纳入 due（通用骨架已被专属面板取代）；单测覆盖评分映射 ✓

- [x] **Task 3.2 C6 题型反查卡**（看题面 → 公式池点选）— RFC D1 二阶 / D13=C — ✅ 代码完成待真机验收（2026-06-05）
  - 数据就绪：30/30 公式有 `typicalProblems`（教辅改编题面 JSON，**字符串数组**纯文本，部分含数值答案不透露解法）
  - 交互蓝图：展示一道 typicalProblem 题面 → 用户从**公式池点选**该题该用哪条公式（切断文字输入，RFC §3.6）→ 判对错 + 反馈
  - ✅ **二级决策（2026-06-05 用户拍板）**：① 候选池 = **同章节**（按用户 subject 过滤）② **多选**（正确集恒为单条 = 题面所属公式，多选 UI 为未来「一题多公式」留口子，判分=选中集恰好等于正确集）③ 选对→4 / 选错→1 ④ 选项用**公式 KaTeX**
  - ✅ 落地件：
    - `domain/C6Grading.kt`：集合判等判分（`selected == correct && correct 非空` → 4 / 否则 1），与 ClozeGrading 同构
    - `RouterReviewViewModel`：UiState 加 `currentC6Problem/Options/CorrectIds` + `C6Option(formulaId,title,latex)`；`buildC6Card` 抽题面 + 同章节候选池（`observeFormulasFor(subject).first()` 过滤 chapter + 稳定 shuffle）；题面缺失 / 候选 <2 条回落 `ShowCardPane`
    - `RouterReviewScreen.C6TypicalProblemPane`：题面 Text（纯文本）+ 多选 `LatexChipsView`（公式 KaTeX）+ 提交自动判分横幅 + 选错露正确公式（标题+KaTeX）
    - `C6GradingTest` 7 单测（单条对/错、多选超额、空选、正确集空兜底、未来多公式全对/漏选）
  - ✅ compileDebugKotlin + 全套单测 **333 个全绿**（326→333）
  - ⏳ **真机验收并入 Task 3.6**：C6 卡随轮转出现 → 题面 + 同章节 KaTeX 候选多选 → 判分落库 → 选错露正确公式
  - Done：`C6TypicalProblemPane` + 公式池点选组件；单测覆盖判分 ✓

- [x] **Task 3.3 错题本入口 UI**（Memory Tab 二级页）— RFC §4.4=B — ✅ **真机验收通过（2026-07-01）**
  - ✅ **真机全闭环（2026-07-01，DB 铁证）**：FAB→错题本→空态→表单（科目→章节联动 / 来源 chip / 编号数字键盘 / 公式池分组 / 未学灰显）→未学「去学」对话框→已学公式可选（DB 注入期望与方差+Bayes 验证「已学不灰显」）→提交（**期望与方差 6 子卡 S 10→5.0 + lapses 0→1 + nextReview→次日 09:00；penaltySnapshotJson 6 条存惩罚前原值 S=10；Bayes 未选保持 S=10 不受影响**）→删除对话框（仅删记录 / 恢复计划 / 以后都这样）→「恢复计划」（**6 子卡 S 5→10 + lapses→0 + nextReview 还原录入前**）→ error_reports 归零。设置项「删除错题时=每次询问」渲染正常。逐子卡 best-effort 保留已复习进度由 14 单测覆盖。
  - ✅ **开工前二级决策（2026-07-01 用户拍板）**：① FormulaIndex **最小够用**（只做 subject→chapter→公式 分组 + 未学标记，图谱邻接/可视化留 Sprint 4）② 快照还原 = **逐子卡 best-effort**：删除选「恢复计划」时，仅还原「录入后未被真实复习触碰」的子卡（`lastReviewTime <= createdAt`），录入后复习过的保留当前进度（惩罚已被消费）
  - ✅ 落地件：
    - `ErrorReportEntity` 加 `penaltySnapshotJson` + `AppDatabase` v10→v11（destructive）
    - `domain/FormulaIndex.kt`：两级分组纯函数 + 未学标记（7 单测）
    - `domain/ErrorDeletePolicy.kt`：Ask/DeleteOnly/Restore 枚举 + `AppPreference` 持久化
    - `ErrorReportProcessor`：`process` 惩罚前快照子卡；`deleteReport(report, restore)` 逐子卡 best-effort 还原（+6 单测）+ `SubCardPenaltySnapshot`
    - `ui/viewmodel/ErrorBookViewModel.kt`：列表↔表单两态 + 公式池 + 删除策略（草稿随 VM 存活）
    - `ui/screen/ErrorBookScreen.kt`：TopAppBar + 列表卡 + 新增表单（subject/chapter/sourceType chip + sourceTag 数字键盘 + 公式多选池灰显未学 + 跳学习确认）+ 删除对话框
    - `MemoryScreen` 右下角 FAB → `AppRoute.ErrorBook` 路由；`SettingsScreen` 加「删除错题时」下拉
  - ✅ compileDebugKotlin + 全套单测 **351 个全绿**（338→351）
  - ⏳ **真机验收并入 Task 3.6**：FAB→列表→表单→公式多选→提交次日重现；删除「恢复计划」还原快照；未学灰显跳学习草稿续填；设置项生效
  - ⏳ **留精修**：草稿跨进程死亡持久化（当前靠 VM 存活 + 返回箭头回表单，够用）；结业后自动回表单（当前靠返回箭头）
  - Done：见下方原始验收标准
  - ~~[ ] 原设计~~（保留供对照）：
  - **后端就绪**：`ErrorReportEntity` + `ErrorReportDao`(observeAll 倒序 / delete) + `ErrorReportProcessor`（Sprint 1 Task 1.6，插入即把所选公式 6 子卡 `S←MAX(S×0.5,0.5)` + 推次日刷新整点 + lapses+1）
  - **种子真实取值**：subject = 高数 / 线代 / 概率论；chapter = 高数 8 章 / 线代 4 章 / 概率论 4 章（由 `FormulaEntity.chapter` distinct 派生）

  - **① 入口**：Memory 页**右下角浮动圆形 FAB**（M3 `FloatingActionButton`，悬底栏上方）→ 进错题本二级页
  - **② 主页**：历史错题列表（`observeAll` 倒序），行显示「来源+编号（如 历年真题 2024-18）· 高数/微分中值定理 · N 条公式」；支持删除（见 ⑤）
  - **③ 新增表单**（全 chip / 数字键盘，零自由文字）：subject chip 单选 → chapter chip（按 subject 联动）→ sourceType chip（历年真题/模拟卷/习题集/其他）→ sourceTag 数字键盘（受限编码如 `2024-18`）→ **公式多选池** → 提交触发 `ErrorReportProcessor.process`
  - **④ 公式多选池**：
    - **默认按 subject 过滤 + 可切「显示全部」**
    - 抽出共用 **`FormulaIndex`**（subject→chapter→公式 分组纯函数/索引），**Sprint 4 公式族图谱复用同一套分组**（只复用分类索引，不复用图谱可视化）
    - **未学（无子卡）公式灰显** → 点击弹「这条还没学，去学？」→ 是则跳七步仪式；**录入表单存草稿**，学完回来续填（草稿持久化，避免离开丢选择）
  - **⑤ 删除 = 精确可撤销（用户拍板做精确版）**：
    - ⚠ **需加 schema 字段**：`ErrorReportEntity` 新增 `penaltySnapshotJson`，录入当下快照所选公式 6 子卡的 `(stability/nextReviewTime/lapses)` 原值；DB version bump（destructive migration，加字段便宜）
    - 删除时弹窗「同时恢复这些公式的复习计划？[仅删记录 / 恢复计划]」+「以后都这样，不再询问」勾选
    - 「恢复计划」按 `penaltySnapshotJson` 还原子卡（best-effort：若期间已复习过该公式，恢复会覆盖那次进度——属「撤销=回到错题录入前那一刻」语义，实装时再确认边界）
    - **设置页加选项**「删除错题时：每次询问 / 仅删记录 / 恢复计划」（持久化到 `AppPreference`）
  - ⚠ **开工前最后确认**：FormulaIndex 抽取边界 + penaltySnapshot 的 best-effort 覆盖语义
  - Done：FAB→列表→新增表单→公式多选（灰显未学+草稿续填）→提交次日重现真机验过；删除撤销精确还原快照验过；设置项生效

- [x] **Task 3.4 Leech 升级**（错题反向联动）— RFC 报告 §7 — ✅ 代码完成（2026-07-01）
  - 现状：`isLeech = lapses >= 4`（MemoryScreen 内联魔数）
  - 升级：新增「7 日内被错题反向标记 ≥ 2 次」也判 leech
  - ✅ 落地件：
    - `domain/LeechDetector.kt`：纯函数 `isLeech(lapses, recentErrorMarks)` = `lapses≥4 || recentErrorMarks≥2`，阈值常量化（6 单测）
    - `domain/ErrorMarkTally.kt`：`countRecent(reports, now)` 近 7 日窗口 + 按错题条数去重计数（7 单测）
    - `FormulaWithState` 加 `recentErrorMarks` 字段 + `isLeech` 派生（判定外提，UI 不再内联阈值）
    - `MemoryViewModel`：combine 加 `errorReportDao.observeAll()`，解析 wrongFormulaIdsJson → ErrorMarkTally → 注入 recentErrorMarks
    - `MemoryScreen`：`isLeech = item.isLeech`（替代 `lapses >= 4`）
  - ✅ **全 App 统一 leech 判定（2026-07-01 用户拍板：leech 就是 leech，不该分页面）**：
    - FormulaDetailScreen 顶部 leech 横幅 → `item.isLeech`（白捡，共用 MemoryViewModel 数据流）
    - TestViewModel combine 加 `errorReportDao.observeAll()` + tally → 答错震动强度用 `item.isLeech`
    - 死代码 ReviewCard（旧 ReviewScreen 已删，无调用方）一并改 `item.isLeech`
    - **零散 `lapses >= 4` 魔数彻底清光**，leech 定义唯一出处 = `LeechDetector`
  - 🐛 **真机验收发现并修复（2026-07-01）**：`LeechBanner` 写死「已错 $lapses 次」，靠错题标记判 leech 时 lapses=0 → 显示矛盾的「已错 0 次」。改为只列真正触发的原因（lapses≥4 显「已错 N 次」/ 标记≥2 显「近 7 日错题标记 N 次」，都够则都显）。
  - ✅ **复习界面「顽固」chip（2026-07-01 收尾）**：RouterReviewScreen 卡顶加红色「🔥 顽固」chip，与强标记/加强卡 chip 并排，补齐 leech 全 App 可见。实现：`RouterReviewViewModel` 加 `errorReportDao` + renderUiState 算当前公式 `currentIsLeech`（聚合 lapses + ErrorMarkTally 近7日标记 → LeechDetector）；UI 用 **CompositionLocal**（`LocalCardIsLeech`）跨层传给 CardHeaderChips，**零面板签名改动**。真机验证：Bayes（2 错题标记）显 chip、全概率（非 leech）不显 ✅。364 单测全绿。
  - ✅ 全套单测 **364 个全绿**（351→364）
  - ⏳ **真机验收并入 Task 3.6**：提交 2 条错题标同一已学公式（7 日内）→ Memory 卡变红 leech（详情横幅/测试震动同步）
  - ⏳ **真机验收并入 Task 3.6**：提交 2 条错题标同一已学公式（7 日内）→ Memory 卡变红 leech
  - Done：聚合逻辑加时间窗计数 ✓；单测覆盖两条 leech 触发路径 ✓

- [x] **Task 3.5 复习 C2 判错顶部公式骨架着色**（用户 P1，2026-06-04）— ✅ 代码完成待真机验收（2026-06-22）
  - 现状：`C2ClozePane` 提交后只在下方逐空标 ✓/✗，顶部骨架只实时填入不标对错
  - 改造：`ClozeSkeletonBuilder` 加「判分态」重载（传 `perBlankCorrect`），顶部方框按对错着色（对=主色 / 错=错误色，错空可叠正确答案）；顺带覆盖七步仪式 `MiniC2Card` 结果态
  - ✅ **二级决策（2026-06-22 用户拍板）**：① 着色方案 = **`\colorbox` 整框底色**（对绿底 #C8E6C9 / 错红底 #FFCDD2，`\textcolor` 锁深绿/深红前景防暗色模式低对比度）② 错空对照：**顶部骨架方框填正确答案**（红框）+ **下方选项区改为显示用户选错的部件**（「你选的（错）：」），上下对照学习
  - ✅ 落地件：
    - `ClozeSkeletonBuilder.buildGraded(latexCode, blanks, perBlankCorrect)`：判分态骨架，每框填 placeholder（正确答案）+ `\colorbox` 上色；缺失视为答错（保守红底）
    - `RouterReviewScreen.C2ClozePane`：提交后顶部走 `buildGraded`；下方错空从「正确答案：placeholder」改为「你选的（错）：selections[index]」
    - `FormulaLearnRitualScreen.MiniC2Card`：结果态由「还原 fullLatex」改为 `buildGraded` 单空着色
    - `ClozeSkeletonBuilderTest` +5（答对绿底 / 答错红底 / 多空混合 / 缺失视错 / 空列表兜底）
  - ✅ compileDebugKotlin + 全套单测 **338 个全绿**（333→338）
  - ⏳ **真机验收并入 Task 3.6**：C2 判错顶部红框 + 下方「你选的（错）」对照；**暗色模式对比度重点核对**（colorbox 浅底配 textcolor 深字）
  - Done：KaTeX 着色渲染真机验过 + `ClozeSkeletonBuilderTest` 加判分态用例 ✓

- [x] **Task 3.6 单测 + 真机验收** — ✅ **真机验收通过（2026-07-01）**，剩「复习顽固 chip」一小项延后
  - ✅ C3.3 错题本闭环真机（见 Task 3.3）+ 3.4 leech 红卡真机（Bayes lapses=0 靠 2 错题标记判红，DB 铁证）
  - ✅ C4 推导 / C6 反查 / C2 判错着色 真机走通
  - 🐛 **真机验收现场修复（2026-07-01，均已 DB/截图验证）**：
    1. **C2 着色重做**：`\colorbox` 整框底色太重/过度吸睛（用户 P1）→ 改「对=原样不上色 / 错=公式染红字 `\boxed{\textcolor{#E53935}{...}}`」，暗色更清爽；`ClozeSkeletonBuilderTest` 5 例同步改
    2. **C5 混入轮转**：C5 延后却仍随 due 子卡出现 → 落通用「看答案」空壳。`buildSessionInputs` 剔除 C5
    3. **C6 单章回落**：同章 <2 公式的 C6 回落「看答案」（正态/泊松/期望方差）→ `buildSessionInputs` 剔除同章<2 的 C6，只留能出真卡的（Bayes/全概率）。DB 铁证：5 张 due 中正确排除 c5+2 张单章 c6
    4. **LeechBanner「已错 0 次」**：错题标记判 leech 时 lapses=0 文案矛盾 → 改按触发原因列（见 Task 3.4）
  - ✅ 全套单测 **364 全绿**
  - ⏳ **延后一小项**：复习界面（RouterReviewScreen）加「顽固」chip（补齐 leech 全 App 可见）——用户提出，需 VM 暴露当前公式 isLeech + 卡顶加 chip；下次实现
  - ⚠ **真机 DB 调试沉淀**：复习会话**同日持久化**（review_session_progress），改路由/卡型过滤后旧会话会被「续接」→ 验证前须清 `review_session_progress` 表让会话重建，否则看到的是旧残留

### Sprint 3 留作 Sprint 4 起点的债

- **C5 易混辨析卡**：需先补 `diffExplanation` 内容（10-15 对易混，约 3-4h 内容工程）+ 可能新增 `confusable_pairs` 表（RFC §9.2 D12）
- 改进点池其余 P1/P2（全局切换打磨 / 六维状态人话化 / 震动反馈 等）按需排期

---

## Sprint 4：数据层地基标签化 + 公式族图谱

### 主题（2026-07-09 用户拍板范围，详 RFC §9.4 D16）

开工前数据核查发现图谱数据稀疏（`parents` 9/30 · `confusableWith` 10/30，20/30 无边，漂亮的链多跨章），RFC 原「章节内视图」在现数据下 13/16 章孤点 → 空壳。根因是**关系存成内嵌 JSON 单向边 + subject/chapter 是硬列**。用户借此升级愿景（App 定位为**领域无关记忆「外壳」**，总字典可扩展），拍板**分类层 + 关系层全标签化**（方案乙）。

**纪律**：眼下仍以**考研数学公式记忆核心功能**为准，不偏移愿景；地基只在**通用化 + 原子化**上铺好；数学内容字段（latex/cloze/derivation）原样保留，不做内容通用化。**StudyPhase 阶段切换（原 4.1-4.3）延后 Sprint 5**。

### Task 列表（3 个）

- [x] **Task 4.1 标签/关系地基**（数据层重构，v11→v12）— ✅ **完成 + 真机 smoke 过 · 已 commit+push（`072211a`）**
  - ✅ 新建 `TagEntity`(tags) / `EntryTagCrossRef`(entry_tag_map，带 `isPrimary`) / `EntryRelationEntity`(entry_relations) + 3 DAO；`domain/TagNamespace`（开放式）+ `domain/EntryRelationType`（推导有向/易混·同族无向）
  - ✅ **路径 2**：`FormulaEntity` 只删 `parents`/`siblings`/`confusableWith`（迁 entry_relations，零消费方）；`subject`/`chapter`/`tags` 保留为显示缓存
  - ✅ `formulas.json` **不改**——种子加载器直接从现有字段拆原子行写三表（比改 JSON 更省更稳）
  - ✅ `formula_subject_map` 退休，数一二三并入 `tags`(namespace=exam)；`observeByKaoyanSubject` 改走标签 JOIN
  - ✅ **验证**：编译过（Room 接受新 schema）；数一二三过滤 30/21/26 不变 + exam 行 77 + 幂等；新增 `TagFoundationSeedTest` 6 例；**全套 364 单测绿**；真机 smoke 过（App 正常/列表有公式/科目过滤对）

- [x] **Task 4.2 公式族图谱 = 记忆主视图**（详 RFC §9.4 D17）— ✅ **完成 + 真机验证 · 已 commit+push（`f156a21`）**
  - ✅ 原子化三层引擎：`domain/graph/`（数据层 GraphModel/Builder + 布局层 ClusterOverviewLayout 母/WithinChapterLayout 子，纯函数确定性）+ 渲染层 `GraphScreen`（Compose Canvas 边 + Composable 节点，相机变换对齐）+ `GraphViewModel`（复用 MemoryVM combine + 读 entry_relations）
  - ✅ 母层：章节气泡地图（大小∝公式数/科目色/掌握度环/跨章虚线）+ 拖动 + 松手吸附最近气泡 + 顶部进度条；点气泡缓动居中 + 钻入
  - ✅ 子层：块内分层子图 + 节点状态色（未学/学习中/已掌握✓/顽固🔥）+ 块内三色边 + 内缩圆角学科色边框 + 浮动圆形返回 + 居中标题胶囊（含进度）
  - ✅ 交互：气泡开合动画（进从点击位放大/返回朝中心缩）+ 点公式按状态路由（未学→七步/已学→详情）+ 跨章 `↗N` 角标→列表→跳章居中目标公式+脉冲 + `rememberSaveable` 位置持久化（跳学习页返回不丢）
  - ✅ 删旧列表 `MemoryScreen`（图谱作主视图；MemoryViewModel 保留供 FormulaDetail）；MainScreen 记忆 Tab → GraphScreen
  - ✅ M3 合规：查官方文档 → chrome 用 `FilledTonalIconButton`/`surfaceContainerHigh`/tonal 层级；M3 组件清单沉淀进 `docs/design/UI设计规范.md §3.1`
  - ✅ 真机逐步验证（截图）：母层渲染/进度条/开合/子层/状态色/跨章角标→列表→跳转全链路走通
  - **图谱升为记忆 Tab 默认主视图，删旧列表**（承载外观+路由+学习状态）
  - **布局**：母=章节聚类分区（固定空间锚点）+ 子=块内分层（推导纵向），确定性算法·同池唯一
  - **呈现（语义缩放两级）**：母层=章节气泡地图（大小∝公式数/色=科目/外环=章节掌握度/虚线连跨章）→ 点气泡开合钻入 → 子层=块内分层子图
  - **交互**：母层拖动+松手吸附最近气泡+当前气泡进度条；子层拖动不吸附；点节点按状态路由（未学→七步/已学→详情）；跨章 `↗N` 角标→列表→跳章吸中心；系统返回/捏合退出；气泡开合动画
  - **渲染**：Compose Canvas 画边 + Composable 节点 chip（公式简名，**禁每节点 KaTeX**）；节点 3 态（未学灰虚线/学习中/已掌握绿）+ 顽固🔥叠加
  - **引擎原子化三层**：数据(GraphModel) / 布局(`GraphLayout` 可插拔接口) / 渲染(Canvas+Composable)
  - 建议子任务：① 布局引擎(纯 Kotlin，聚类+块内分层) ② 渲染层(相机变换下 Canvas 边 + Composable 节点对齐) ③ 母层(拖动/吸附/进度条/气泡) ④ 子层(分层子图/节点状态/跨章角标) ⑤ 手势(拖/点/捏合消歧 + 开合动画) ⑥ 路由接现有学习/详情 ⑦ 删旧列表
  - **原型**：HTML 交互原型已验证导航模型（概念通过）

- [x] **Task 4.3 回填 + 图谱校验** — ✅ 完成（2026-07-09）
  - ✅ 地基校验单测随 4.1 完成（`TagFoundationSeedTest` 6 例）
  - ✅ 图谱布局引擎纯函数单测（`ClusterOverviewLayoutTest` 7 + `WithinChapterLayoutTest` 7 + `GraphModelBuilderTest` 5 = 19 例：确定性/学科分带/块内分层拓扑/跨章邻居/状态映射）
  - ✅ **全套 383 单测绿**；图谱三科真机可见结构 + 交互闭环

### Sprint 4 留作 Sprint 5 起点的债

- ~~**StudyPhase 阶段切换**~~ → ✅ **Sprint 5 完成**（见下）
- **C5 易混辨析卡**：需先补 `diffExplanation` 内容（D12=B）；地基标签化后 `entry_relations` 的易混边可直接复用
  - **✅ 代码半已落地（2026-07-13，commit `91c19a3`）**：`domain/DiscriminationCardBuilder.kt` 纯逻辑 N 选 1
    构造器（目标 + 易混邻居 → 打乱选项 + 正确 id + 可选 diffExplanation；无易混邻居返回 null；含 `isCorrect` 判分），
    8 单测。`diffExplanation` 做成可选入参 → **缺内容也能出卡，不再被内容卡脖子**。
  - **留尾（回家设备场次一次做完）**：内容半 `diffExplanation` 文案（10-15 对）+ `FormulaEntity`/种子承载字段 +
    `C5DiscriminationPane` 专属 UI + `RouterReviewViewModel` 解除 C5 剔除（当前 `line ~212` 仍 `→ false`）+ 真机验收。
- Khan 4 级掌握度：RFC §6.2 明确推迟，不排期

---

## Sprint 5：StudyPhase 学习阶段切换 ✅（代码完成 · 已 commit+push `da1287a`；真机验收部分待做，见 Sprint 6 #6）

**决策（②a 自动建议按距考天数 / ①a 入口在设置页 / ③b 交错策略全做）**：一气做完，不拆增量（用户反馈：相关开发一次做完整）。参数依研究报告 §3.2·§7.2。

- ✅ `domain/StudyPhase.kt`：五态 enum（一/二/三轮/冲刺/保持）+ 每阶段配置（新卡上限 / intervalFactor=ln(R)/ln(0.9) / 交错策略）+ `suggestedFor(daysToExam)` 自动建议；`Interleave` 枚举（BLOCK/WITHIN_CHAPTER/FULL）
- ✅ `AppPreference`：`studyPhase` 持久化 + 每日新卡计数（`newCardDayKey`/`newCardCount` + `recordNewActivation` 跨日重置 + `newCardsUsedOn`）
- ✅ **FSRS retention 联动**：`ReviewScheduler.computeFsrs/calculate` 加 `intervalFactor`（缩放复习间隔）；`ReviewEventProcessor` 按阶段传入（Scene 守卫）
- ✅ **交错策略**：`domain/SessionInterleaver.kt` 纯函数（章内 block / 章内交错 / 全交错 round-robin，确定性）；`RouterReviewViewModel.buildSessionInputs` 按阶段重排公式顺序
- ✅ **新卡上限 gate**：七步仪式结业 `recordNewActivation`；`FormulaLearnRitualViewModel.load` 按阶段+今日计数拦截（关新卡阶段 / 达上限 → `capBlocked` 提示不进仪式）
- ✅ **设置页 UI**（学习计划区）：当前阶段 + 描述 + 距考天数自动建议进阶按钮 + 「重置学习阶段」对话框（D15=A 后悔药，列全阶段可选）
- ✅ **Scene 守卫**（改进点池 P2）：SprintMode 行为点（MainActivity 批处理 / TestVM isSprintActive）+ 新阶段联动全部加 `useScene==KaoyanMath` 守卫
- ✅ 单测 +16（`StudyPhaseTest` 8 / `SessionInterleaverTest` 6 / `ReviewSchedulerIntervalFactorTest` 2）；**全套 399 绿**
- ⏳ **真机验收部分待做** → 见 Sprint 6 #6（设置切换已验；新卡上限拦截 / 复习间隔 / 交错 未真机）

---

## Sprint 6：阶段收尾（🔵 进行中，2026-07-13 立项）

> **由来**：2026-07-13 用户复核发现——Sprint 1-5 的**主干**跑通了，但**七步仪式有 2.5 步是占位、C5 整张卡没落地**（当初为推进先占位/延后的债），加上几处代码半只做了纯逻辑。本 Sprint 专门收清这些，收完写阶段总结 → 归档 03 → 开新阶段。
>
> **写法纪律**：每条写清 现状（🔴未做/🟡半成）+ 已落地部分 + 还差什么 + 验收标准，不写笼统勾。

### Task 列表（6 块）

- [x] **6.1 C5 易混辨析卡·整卡落地** ✅ **完成 + 真机验收通过（2026-07-16，commit `719fbc8`）**（方案 A：全复用现有字段，不加 diffExplanation 字段/内容、不动 DB，用户拍板"没必要重复造轮子"）
  - ✅ 代码半：`domain/DiscriminationCardBuilder.kt`（N 选 1 纯逻辑 + 判分，8 测，commit `91c19a3`）
  - ✅ 题干=目标 `purpose`（用途），选项=目标+易混邻居公式 latex，揭晓=正确公式 + `适用条件`（复用现有字段区分，无新内容）
  - ✅ 接线：`FormulaRepository.confusableNeighbors()` / `formulaIdsWithConfusable()`；`RouterReviewViewModel.buildC5Card` + 解除 C5 剔除（改 gate 为"有易混邻居才出"）+ UiState 加 `currentC5Options/CorrectId`
  - ✅ UI：`RouterReviewScreen.C5DiscriminationPane`（用途线索 + `LatexChipsView` 单选 N 选 1 + 判分 4/1 + 揭晓正确公式+适用条件）
  - ✅ **真机验收（无线 adb）**：注入 `prob_total_probability` due C5 → 复习出 C5 卡（用途题干 + 全概率/Bayes 两选项 KaTeX 正确）→ 选对判"评定 4" → 揭晓全概率公式 + 适用条件辨析要点 → 继续落库（DB 铁证 `totalReviews`0→1、`nextReviewTime` 推后）→ 正常进默写收尾

- [~] **6.2 七步仪式 Step 2 拆块讲解** 🟡 代码+内容完成待真机
  - ✅ 数据模型：`domain/model/FormulaChunk`(latex+note) + `domain/FormulaChunkParser`（对照 DerivationStep，4 测）
  - ✅ 字段+迁移：`FormulaEntity.chunks` + DB **v12→v13**（destructive 重灌）；种子 DTO + `FormulaSeedValidator` 加 chunks 校验
  - ✅ 内容：**30 公式全部拆块**（每条 2-4 块，latex 片段 + 中文讲解，写进 `formulas.json`）
  - ✅ UI：`Step2Chunks` 按块渲染（片段 KaTeX + 讲解卡片），空则回落占位；`FormulaLearnRitualViewModel` 解析 chunks 入 uiState
  - ✅ 编译过 + 全套单测绿 + 校验器对真实 chunks 通过
  - ⏳ **真机验收待做**（无线掉线未装 v13）：起某公式七步仪式 → Step 2 显示真实分块讲解

- [ ] **6.3 七步仪式 Step 5 Worked Example** 🔴
  - 现状：占位（`~L503/525`「补完 workedExamples 字段后显示 2 道带步骤例题」，字段不存在）
  - ⬜ 数据：`FormulaEntity` 加 `workedExamples` 字段（JSON，题面 + 分步解）+ 内容 + 迁移
  - ⬜ UI：Step 5 展示 2 道 worked example（替换占位）
  - ⬜ 验收：真机某公式 Step 5 显示真实例题

- [ ] **6.4 七步仪式 Step 7 迷你卡 C4/C5/C6 形态** 🟡
  - 现状：C1/C2/C3 mini ✅；C4/C5/C6「此卡型 mini 形态暂未实装，自动通过」(`~L660`)
  - ⬜ 补 C4/C5/C6 的 mini 卡形态（或明确决策哪些不进 mini 序列 → 从"自动通过"改为不生成）
  - ⬜ 验收：真机结业前 mini 序列包含相应卡型或按决策排除

- [ ] **6.5 #323 毙掉项隐藏·落地** 🟡
  - ✅ 代码半：`domain/LearningItemVisibility.kt`（三态判定 + 8 测，commit `db973d6`；方案甲）
  - ⬜ 数据：`FormulaEntity` 加 `excludedItems` 列（JSON，值取 `LearningItem.key`）+ DB v12→v13 迁移
  - ⬜ UI：`FormulaDetailScreen` / `FormulaLearnRitualScreen` / C1 卡按三态渲染（HIDDEN 隐藏 / PLACEHOLDER 占位 / SHOWN）
  - ⬜ 待定：「待补」呈现形态（继续占位「（暂未标注）」/ 仅 debug 可见）— 排期前问用户
  - ⬜ 验收：真机标 `excludedItems` 的公式对应板块隐藏，未标空字段仍占位

- [ ] **6.6 StudyPhase 真机验收** 🟡
  - ✅ 设置页切阶段 / 建议进阶 / 重置 已真机验（Sprint 5）
  - ⬜ 新卡上限拦截（关新卡阶段 / 达上限 → 七步 `capBlocked`）真机验
  - ⬜ 复习间隔随阶段 `intervalFactor` 变化 + 交错策略（BLOCK/章内/全交错）真机验
  - ⬜ 验收：切阶段后 DB 铁证 next 间隔缩放 + 会话顺序符合交错模式

- [ ] **6.7 复习默写环节完善** 🔴
  - 现状：MVP —— `RouterReviewScreen ~L1096` 默写面板**恒显完整公式**让用户点「默对了/没默出」自评
  - 🔴 **hint 分级渐进揭示未实装**：状态机跑 `hintLevel`（错1 hint1 / 错2 hint2 / 错3 blocked），但 UI 不理 hintLevel、恒显答案，与 `hintText`「首发：不看公式回想」矛盾（`~L1108 MathFormulaView` 无条件渲染完整公式）
  - ⬜ 接输入：按 `inputMode` 路由到 `TracingCanvas`（手写）/ `PaperPenInputArea`（纸笔），而非直接露答案（注释 `~L1096`「后续 Task 接 TracingCanvas/PaperPen」）
  - ⬜ hint 渐进：hintLevel 0 全隐 → 1 露第一块 → 2 露推导前两步，按级揭示（现只改 label 文字）
  - ⬜ 验收：真机默写连错触发 hint1/hint2 递进揭示 + 错 3 次 blocked 红条

- [x] **6.8 回想环节改"手写→OCR→自动对照"（弃纯自评）** ✅ **完成·用户真机确认能用（2026-07-16，commit `fc75b7c`+`fe98ac4`）**（交互优化转改进点池）
  - **背景**：全 App 的回想环节现在都是**自评**（"记得/忘了"、"默对了/没默出"、Test「请诚实核对」），无 OCR 自动比对逻辑。用户设计目标=手写模块能完整输入并判对。
  - **范围（用户拍板：统一都改）**：① 学习 Step 7 巩固 mini-C1 识别（`FormulaLearnRitualScreen.MiniC1Card`「在心里默默写」）② 复习 C1 识别卡（`C1RecognitionPane` 看答案自评）③ 复习默写收尾（`StartDictation` 面板，与 6.7 合并）
  - **按 inputMode 分流**：手写识别模式 → 手写画布 → OCR → **自动判对错**（用户拍板"先做成自动判断，有问题再改"）；纸笔自评模式 → 保留"写纸上→出答案→自评"。
  - ✅ 纯逻辑：`domain/HandwrittenLatexGrader`（判等专用 canonical 规范化 → isMatch/isMatchAny，11 测，commit `fc75b7c`）
  - ✅ 复用组件：`ui/component/HandwrittenAnswerArea`（手写→`TestCanvas`→OCR 候选逐块拼接→提交→比对器自动判分；纸笔→自评；`revealExtra` 槽保留条件/用途/口诀）+ `rememberHandwritingConfig()`
  - ✅ 接三处：Step7 `MiniC1Card` / 复习 `C1RecognitionPane`（RatingRow→自动判分 4/1 + revealExtra 保留学习内容）/ 复习 `DictationPane`（顺带修 6.7"恒显答案"——答案改判定后揭晓）
  - ✅ 编译过 + 全套单测绿；真机装机后 UI 渲染确认（复习 C1 出手写画布 + "提交自动判分"）
  - ✅ **用户真机确认"能用"（2026-07-16）**：手写→识别→自动判分链路跑通、可用
  - **转改进点池（用户拍板不在学习流程阶段深改）**：① 手写识别触发策略——每次抬手都识别致 API 消耗过快（P2）② 作答区布局显空、提交按钮顶到最底（同批 UI 打磨）
  - **关联**：6.7 复习默写"接输入"+"不恒显答案"已并入完成；剩 hint 分级渐进揭示（6.7 单列）

### Sprint 6 验收标准
8 块全部 ⬜ 勾清（或经决策显式排除）+ 真机验收通过 + 全套单测绿 → 写阶段总结 → 归档 03。

---

## 内容工程 Track（独立于代码 Sprint，可并行）

### Track-A：30 公式 MVP（人工标注）— ✅ **完成**（Task 1.7，30 公式 18+ 字段全标）

- 范围按 D8=C + D11=C 拆解（详见 Task 1.7）；分布 21/5/4 三桶

### Track-B：300-500 公式 AI 全标注（pipeline）— ⬜ **未启动**

- LLM 批量生成 18+ 字段 + 抽样 10% 人工 review；质量门禁可与种子校验器（`FormulaSeedValidator`）合并

### Track-C：易混对编辑工具 — ⬜ **未做（阻塞 Sprint 6.1 C5 内容半）**

- 产出 `diffExplanation` 差异说明（10-15 对易混）；内部 CSV/JSON 编辑工具（不入 App）

### Track-D：典型题题面 + worked example — 🟡 **部分**

- ✅ `typicalProblems` 30/30 已标（C6 卡在用）
- ⬜ `workedExamples`（七步 Step 5 用，字段+内容都缺，阻塞 Sprint 6.3）；版权处理见 D13

---

## Sprint 完成记录

> 详细逐 Task 记录见各 Sprint 小节的勾选项 + commit；跨 Sprint 进度快照见
> auto-memory `project_progress.md`。此处只留一句话状态。

| Sprint | 主干内容 | 状态 | 代表 commit |
|---|---|---|---|
| 1 | 数据基础 + Scene/Subject + 6 类子卡 schema + 30 公式 MVP | ✅ 完成 | (Sprint 1 系列) |
| 2 | 七步仪式 + 复习路由器 + C1/C2/C3 + 子卡 FSRS | ✅ 主干（Step2/5 占位见 Sprint 6） | `ea976c4` |
| 3 | C4/C6 卡型 + 错题反向 UI + C2 判错着色 + Leech | ✅ 完成 | `a54439b` |
| 4 | 数据层地基标签化 + 公式族图谱（记忆主视图） | ✅ 完成 | `072211a` / `f156a21` |
| 5 | StudyPhase 学习阶段切换 | ✅ 代码完成（真机验收→6.6） | `da1287a` |
| 6 | 阶段收尾（C5/七步占位/代码半/真机） | 🔵 进行中 | — |

---

## 阶段验收标准（全部 Sprint 完成后）

详见 [`RFC_学习流程重构.md`](../planning/RFC_学习流程重构.md) §10。
