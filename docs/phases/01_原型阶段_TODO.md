# 考研数学公式记忆助手 (Formula Master) — 原型阶段执行计划（已归档）

> **🗄️ 归档说明**
> 本文件为**原型阶段**的历史执行计划，Sprint 1~6 全部完成（2026-04-24 收尾）。
> 当前进行中的活文件：[`../phases/02_打磨阶段_TODO.md`](02_打磨阶段_TODO.md)
> 阶段性总结：[`../原型阶段总结.md`](../原型阶段总结.md)
>
> ---
>
> **原文档说明**（以下内容保持原样，仅供溯源参考）：
> 本文件是项目唯一的执行计划，所有开发任务以此为准。
> 设计细节请查阅 `Project_Spec.md`（权威设计来源）和 `UI设计规范.md`（组件规范）。
> 本文件只写"做什么、怎么判断完成"，不重复设计细节。
>
> **任务规模约定**：每个 Task 应在 1~3 小时内独立完成，有且仅有一个明确的 Done 标准。
>
> **架构原则**（全程遵守）：
> - 严格 MVVM + UDF，禁止在 Composable 中直接操作数据库
> - OCR 必须通过 `MathOcrRecognizer` 接口注入，UI 层禁止感知具体实现
> - 渲染引擎统一使用 **KaTeX + WebView**，不引入 JLatexMath

---

## 📋 Sprint 概览

| Sprint | 主题 | 核心产出 |
|--------|------|----------|
| Sprint 1 | 基础设施 | 项目跑起来，数据库可用，权限声明完整 |
| Sprint 2 | 渲染引擎 | KaTeX 离线渲染，公式数据入库 |
| Sprint 3 | 记忆模块 | 公式列表 + 详情 + 临摹 + 状态激活 |
| Sprint 4 | 复习模块 | FSRS 算法 + 三步复习交互 + 考前收敛机制 |
| Sprint 5 | 测试模块 | 严格默写 + 惩罚机制 + 热力图 + 顽固节点处理 |
| Sprint 6 | 完善打磨 | 通知、性能、UI 走查 |

---

# 🚀 Sprint 1：基础设施搭建

**目标**：App 可以启动，数据库三张表可用，所有权限声明到位，Navigation 骨架搭通。

---

### ✅ Task 1.1：项目初始化

**操作**
- 在 Android Studio 中创建 `Empty Compose Activity` 项目
- 语言选 Kotlin，Min SDK 选 26（API 26），Target SDK 34

**完成标准**
- App 在模拟器或真机上成功启动，显示默认 Compose 页面
- 项目根目录结构如下（手动创建包目录）：
  ```
  app/src/main/java/.../
  ├── data/
  │   ├── local/
  │   │   ├── entity/
  │   │   ├── dao/
  │   │   └── AppDatabase.kt（暂不创建，Task 1.4 补充）
  ├── domain/
  └── ui/
      ├── screen/
      ├── component/
      └── theme/
  ```

---

### ✅ Task 1.2：Material 3 主题配置

**文件** `ui/theme/Theme.kt`

**操作**
- 配置默认蓝色 `ColorScheme` 作为 Fallback（API < 31）
- API 31+ 开启 `dynamicLightColorScheme` / `dynamicDarkColorScheme`
- 通过 `isSystemInDarkTheme()` 自动切换深浅色
- 在 `MainActivity.kt` 的 `onCreate` 中调用 `enableEdgeToEdge()`
- 在 `Scaffold` 中正确处理 `WindowInsets`（参见 `UI设计规范.md` §2）

**完成标准**
- 切换系统深色模式后重启 App，UI 颜色自动变化
- 内容可以延伸到状态栏区域，没有突兀的黑边白边

---

### ✅ Task 1.3：引入核心依赖

**文件** `app/build.gradle.kts`

**需要添加**
- `androidx.room:room-runtime` + `room-ktx` + `room-compiler`（KSP）
- `androidx.navigation:navigation-compose`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.work:work-runtime-ktx`（WorkManager，Sprint 6 用，现在先引入）
- `com.google.code.gson:gson`（用于解析 `clozeData` JSON）

**完成标准**
- Gradle Sync 成功，无报错
- 在任意 `.kt` 文件中 `import androidx.room.Room` 能正常识别

---

### ✅ Task 1.4：声明权限

**文件** `AndroidManifest.xml`

**操作**
- 添加震动权限：
  ```xml
  <uses-permission android:name="android.permission.VIBRATE" />
  ```
- 添加通知权限（Android 13+ 运行时权限的静态声明）：
  ```xml
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  ```

> ⚠️ `POST_NOTIFICATIONS` 是 API 33+ 的运行时权限，Task 6.1 中需要在代码里动态申请。现在先在 Manifest 中声明，否则 WorkManager 通知在 Android 13+ 上静默失效。

**完成标准**
- `AndroidManifest.xml` 中两条权限声明存在，项目编译通过

---

### ✅ Task 1.5：创建 Room 数据库三张表（Entity）

**路径** `data/local/entity/`

**创建以下三个文件**（字段规范以 `Project_Spec.md` §3 为准，数据库设计细节以 `核心数据库和算法设计.md` §1 为准）：

- `FormulaEntity.kt`：静态公式库表，主键 `formulaId: String`
- `StudyStateEntity.kt`：学习状态表，含 `stability`、`difficulty`、`lapses`、`totalReviews` 等字段，外键关联 `FormulaEntity`，`onDelete = CASCADE`
- `ReviewLogEntity.kt`：流水日志表，主键 `logId` 自增，含 `interactionType`、`costTimeMs` 字段

**完成标准**
- 三个文件编译通过，无 Room 注解报错
- `StudyStateEntity` 的外键约束正确配置（`onDelete = ForeignKey.CASCADE`）

---

### ✅ Task 1.6：创建 DAO 与 AppDatabase

**路径** `data/local/dao/` 和 `data/local/AppDatabase.kt`

**DAO 需要实现的方法**（此阶段仅实现基础版，后续 Sprint 按需补充）

`FormulaDao`：
- `insertAll(formulas: List<FormulaEntity>)`
- `getAll(): Flow<List<FormulaEntity>>`
- `getById(id: String): FormulaEntity?`
- `count(): Int`（用于判断是否需要初始化数据）

`StudyStateDao`：
- `insert(state: StudyStateEntity)`
- `update(state: StudyStateEntity)`
- `getByFormulaId(id: String): StudyStateEntity?`
- `getTodayReviewQueue(currentTime: Long): Flow<List<StudyStateEntity>>`（`nextReviewTime <= currentTime` 且状态为 1 或 2）
- `getMasteredFormulas(): Flow<List<StudyStateEntity>>`（状态为 3）

`ReviewLogDao`：
- `insert(log: ReviewLogEntity)`
- `getLogsByDateRange(start: Long, end: Long): Flow<List<ReviewLogEntity>>`（用于热力图）

`AppDatabase`：以 `@Database` 注解声明三张表，版本号为 1。

**完成标准**
- 调用 `formulaDao.count()` 不报错，返回 0

---

### ✅ Task 1.7：定义 OCR 抽象接口与 Mock 实现

**路径** `domain/`

**创建 `MathOcrRecognizer.kt`（接口）**
```kotlin
interface MathOcrRecognizer {
    // 输入：Canvas 上的笔画点列表（或 Bitmap）
    // 输出：Top-N 个 LaTeX 候选字符串，按置信度排序
    suspend fun recognize(input: OcrInput): List<String>
}

// 输入数据类，同时支持 Bitmap 和笔画两种来源
sealed class OcrInput {
    data class BitmapInput(val bitmap: android.graphics.Bitmap) : OcrInput()
    data class StrokeInput(val strokes: List<List<Pair<Float, Float>>>) : OcrInput()
}
```

**创建 `MockMathRecognizer.kt`（Mock 实现）**
```kotlin
class MockMathRecognizer : MathOcrRecognizer {
    override suspend fun recognize(input: OcrInput): List<String> {
        delay(500) // 模拟网络延迟
        return listOf("\\int_0^1 x^2 \\, dx", "\\frac{d}{dx}f(x)", "\\sum_{n=1}^{\\infty}")
    }
}
```

> 说明：接口设计为可替换。后期只需新增 `MathpixRecognizer`、`OnDeviceRecognizer` 等实现类，UI 层代码零修改。

**完成标准**
- 接口和 Mock 类编译通过
- 在单元测试或临时调用中，`MockMathRecognizer().recognize(...)` 能在 500ms 后返回假数据

---

### ✅ Task 1.8：搭建 Navigation 骨架

**文件** `ui/screen/MainScreen.kt`

**操作**
- 使用 `Scaffold` + M3 `NavigationBar` 搭建三个 Tab 入口：`[记忆]`、`[复习]`、`[测试]`
- 三个 Tab 对应三个占位 Composable（暂时显示文字"TODO: Memory / Review / Test"）
- 配置 `NavHost`，为三个 Tab 注册路由，过渡动画使用 `slideInHorizontally` + `slideOutHorizontally`（参见 `Project_Spec.md` §5.4）

**完成标准**
- 点击底部三个 Tab 可以切换不同的占位页面
- 切换时有横向滑动过渡动画

---

# 🎨 Sprint 2：渲染引擎封装与数据预置

**目标**：KaTeX 能正确渲染公式，深色模式适配，公式数据成功入库。

---

### ✅ Task 2.1：KaTeX 离线包准备

**操作**
- 下载 KaTeX 离线包（`katex.min.js` + `katex.min.css` + fonts 目录）
- 放入 `assets/katex/` 目录

**完成标准**
- `assets/katex/katex.min.js` 文件存在，文件大小 > 100KB（确认完整下载）

---

### ✅ Task 2.2：编写 KaTeX HTML 模板

**文件** `assets/math_template.html`

**要求**
- 引入本地 KaTeX（`file:///android_asset/katex/`）
- 包含 `{{LATEX}}` 占位符
- 注入深色模式 CSS（参见 `UI设计规范.md` §4）：
  ```css
  @media (prefers-color-scheme: dark) {
      body { background-color: transparent; color: #E3E3E3; }
  }
  ```
- body 默认背景透明，不要白色背景

**完成标准**
- 用 Chrome 直接打开该 HTML 文件（替换占位符为 `\int_0^1 x^2 dx`），公式能正确渲染
- 切换浏览器深色模式后，公式字体颜色变为浅色

---

### ✅ Task 2.3：封装 MathFormulaView Composable

**文件** `ui/component/MathFormulaView.kt`

```kotlin
@Composable
fun MathFormulaView(latex: String, modifier: Modifier = Modifier)
```

**实现步骤**
1. 创建 `AndroidView` 包裹 `WebView`
2. 从 `assets/math_template.html` 读取模板字符串
3. 将 `{{LATEX}}` 替换为传入的 `latex` 参数
4. 调用 `webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)`

> ⚠️ 必须用 `loadDataWithBaseURL` 而非 `loadData`，否则本地 KaTeX 资源无法被 WebView 加载。

**完成标准**
- 将 `MathFormulaView("\\int_0^1 x^2 \\, dx")` 放入任意 Composable，运行后屏幕上显示正确的积分公式
- 切换系统深色模式后，公式字体颜色正确反转

---

### ✅ Task 2.4：编写 ClozeData 解析器

**文件** `domain/model/ClozeItem.kt` 和 `domain/ClozeParser.kt`

**操作**
- 定义 `ClozeItem` 数据类（对应 `Project_Spec.md` §3.1 的 JSON 结构）：
  ```kotlin
  data class ClozeItem(
      val index: Int,
      val placeholder: String,  // 正确答案 LaTeX
      val options: List<String> // 候选选项（含正确答案和干扰项）
  )
  ```
- 实现 `ClozeParser.parse(json: String): List<ClozeItem>` （使用 Gson）

> 说明：这个解析器是 Sprint 4 填空交互的基础，现在做好后续零风险。

**完成标准**
- 单元测试：将 `Project_Spec.md` §3.1 中的示例 JSON 传入 `ClozeParser.parse()`，能正确返回 `ClozeItem` 列表，`placeholder` 和 `options` 字段值正确

---

### ✅ Task 2.5：准备静态 JSON 公式数据

**文件** `assets/formulas.json`

**操作**
- 手写至少 5 条考研数学公式，覆盖高数、线代、概率三个科目各至少 1 条
- 每条公式必须包含完整的 `clozeData`（至少 1 个填空项），字段结构以 `Project_Spec.md` §3.1 为准

> 建议包含：泰勒展开式、洛必达法则、行列式展开、全概率公式、期望定义。这些是高频考点。

**完成标准**
- JSON 文件结构合法（用 JSONLint 或 Android Studio 校验）
- 每条公式的 `clozeData` 不为空数组

---

### ✅ Task 2.6：实现首次启动数据预加载

**文件** `data/repository/FormulaRepository.kt`

**操作**
- 在 App 启动时（Application 或 MainActivity）检查 `formulaDao.count()`
- 若为 0，读取 `assets/formulas.json`，解析后调用 `formulaDao.insertAll()`
- 必须在 `Dispatchers.IO` 上执行，不能阻塞主线程

**完成标准**
- 首次安装后启动 App，通过 Android Studio 的 Database Inspector 工具查看 `formulas` 表，能看到至少 5 条数据
- 多次重启 App 后，数据不会重复插入

---

# 🧠 Sprint 3：记忆模块

**目标**：用户能浏览公式列表，进入详情，临摹公式，并标记开始学习。

---

### ✅ Task 3.1：MemoryScreen - 公式分类列表

**文件** `ui/screen/MemoryScreen.kt` 和 `ui/viewmodel/MemoryViewModel.kt`

**操作**
- `MemoryViewModel` 从 `FormulaRepository` 获取公式列表，用 `StateFlow<MemoryUiState>` 暴露给 UI
- 使用 `LazyColumn` 展示公式
- 每个公式用 `ElevatedCard`（`RoundedCornerShape(16.dp)`）展示标题、科目标签
- **顽固节点高亮**：若该公式的 `StudyStateEntity.lapses >= 4`，卡片背景改用 `MaterialTheme.colorScheme.errorContainer`（参见 `Project_Spec.md` §5.5）

**完成标准**
- 列表能展示数据库中所有公式
- 点击任意卡片可跳转详情页（Task 3.2）
- `lapses >= 4` 的公式（可手动在数据库中插入测试数据）显示红色背景

---

### ✅ Task 3.2：FormulaDetailScreen - 公式详情与结构化展示

**文件** `ui/screen/FormulaDetailScreen.kt`

**操作**
- 顶部使用 `MathFormulaView` 渲染完整公式
- 实现公式临摹区域（详见 Task 3.3）
- 底部悬浮 `FilledTonalButton`，文案"标记为开始学习"（状态已激活的公式改为"复习中"并禁用按钮）

**完成标准**
- 进入详情页后公式正确渲染
- 已有 `StudyStateEntity`（`learningState >= 1`）的公式，底部按钮显示灰色"复习中"

---

### ✅ Task 3.3：公式临摹画布（Tracing Mode）

**文件** `ui/component/TracingCanvas.kt`

**操作**
- 使用 Compose `Canvas` API 实现手写绘制（监听 `pointerInput`）
- 底层以 `30%` 透明度显示正确公式的 KaTeX 渲染图像作为描红参考
- 右上角悬浮 `IconButton`（清除图标），点击后清空所有路径
- 路径样式：`strokeWidth = 8f`，`StrokeCap.Round`，`StrokeJoin.Round`（参见 `UI设计规范.md` §5 智能草稿交互）

**完成标准**
- 能在公式上方用手指描红
- 点击清除按钮后画布重置
- 灰色参考公式在清空后依然可见

---

### ✅ Task 3.4：状态激活逻辑

**文件** `MemoryViewModel.kt`（新增方法）

**操作**
- 实现 `activateFormula(formulaId: String)` 方法
- 检查 `StudyStateEntity` 是否已存在；若不存在，创建新记录：
  - `learningState = 1`
  - `difficulty` 初始值继承 `FormulaEntity.difficultyLevel`（映射到 1.0~5.0 区间）
  - `stability = 1.0`（初始复习间隔 1 天）
  - `nextReviewTime = 当前时间 + 1天的毫秒数`
  - `lapses = 0`，`totalReviews = 0`
- 触发轻量级震动反馈 `HapticFeedbackType.TextHandleMove`

**完成标准**
- 点击"标记为开始学习"后，Database Inspector 中 `study_states` 表出现对应记录
- 字段值与上述初始化规则一致
- 操作完成后自动返回列表页，该公式卡片状态更新

---

# ⚙️ Sprint 4：复习模块与核心算法

**目标**：FSRS 调度器实现，三步复习交互完整跑通，考前收敛机制就位。

---

### ✅ Task 4.1：ReviewScheduler - FSRS 调度器

**文件** `domain/ReviewScheduler.kt`

**实现规范**（以 `Project_Spec.md` §4 和 `核心数据库和算法设计.md` §2 为准）

必须实现以下逻辑：
1. **难度更新**：`D_new = clamp(D_old + 0.5 * (3 - R), 1.0, 5.0)`
2. **稳定性更新**：
   - `R == 1`：`S_new = min(1.0, S_old * 0.2)`
   - `R > 1`：`S_new = S_old * (1 + (R - 1) / D_new)`
3. **状态流转**：`S_new > 30` 时自动将 `learningState` 设为 3（Mastered）
4. **测试模式奖励**：传入 `isTestMode = true` 且 R=4 时，`S_new` 额外乘以 1.5
5. **遗忘惩罚**：R=1 时 `lapses++`，若 `isTestMode = true` 且 R=1，强制 `learningState = 1`（无论当前状态）

```kotlin
data class SchedulerResult(
    val newDifficulty: Double,
    val newStability: Double,
    val newLearningState: Int,
    val nextReviewTime: Long,
    val newLapses: Int
)

object ReviewScheduler {
    fun calculate(
        current: StudyStateEntity,
        rating: Int,           // 1~4
        isTestMode: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SchedulerResult
}
```

**完成标准**
- 单元测试覆盖以下 6 个场景，全部通过：
  - R=1（遗忘）：`S_new < S_old`，`lapses` 增加
  - R=4（极易）：`S_new > S_old`，`D_new < D_old`
  - `S_old = 35.0` 且 R=3：`learningState` 变为 3
  - 测试模式 R=4：`S_new` 约为普通模式的 1.5 倍
  - 测试模式 R=1：`learningState` 强制变回 1
  - 难度边界：`D` 不超出 `[1.0, 5.0]`

---

### ✅ Task 4.2：ReviewScreen - 今日任务队列

**文件** `ui/screen/ReviewScreen.kt` 和 `ui/viewmodel/ReviewViewModel.kt`

**操作**
- `ReviewViewModel` 查询 `StudyStateDao.getTodayReviewQueue(currentTime)`，通过 `StateFlow` 暴露队列
- 使用 `HorizontalPager` 展示今日待复习公式列表（每张公式一个卡片）
- 队列为空时展示空状态提示（"今日复习已完成 ✓"）

**完成标准**
- 手动将某条 `StudyStateEntity` 的 `nextReviewTime` 改为过去时间，对应公式出现在今日列表中
- 完成所有任务后显示空状态

---

### ✅ Task 4.3：三步复习交互 - Step 1 盲盒唤醒

**文件** `ui/component/ReviewCard.kt`

**操作**
- 界面中央展示公式标题（大字）
- 标题下方是深灰色 `ElevatedCard` 遮罩，完全遮住公式内容
- 用户点击遮罩后触发展开动画（`AnimatedVisibility` + `expandVertically`），显示 `MathFormulaView`

**完成标准**
- 进入复习页时公式内容被完全隐藏
- 点击遮罩后公式通过动画展开

---

### ✅ Task 4.4：三步复习交互 - Step 2 填空强化

**文件** `ReviewCard.kt`（续）

**操作**
- 公式展开后不显示完整公式，而是通过 `ClozeParser` 解析 `clozeData`
- 渲染带 `[ ? ]` 占位符的不完整公式（通过 KaTeX HTML 模板传入处理后的 LaTeX）
- 屏幕下方展示 3~4 个 M3 `FilledTonalButton` 候选选项
- 用户点击后高亮显示正确答案（无论选对选错均可继续）

**完成标准**
- 填空题正确渲染，占位符 `[ ? ]` 清晰可见
- 选择后正确答案高亮，错误答案标红
- 点击任意选项后自动进入 Step 3

---

### ✅ Task 4.5：三步复习交互 - Step 3 FSRS 自评落库

**文件** `ReviewCard.kt` 和 `ReviewViewModel.kt`（续）

**操作**
- 填空完成后通过 `AnimatedVisibility` 滑出底部 4 个自评按钮（参见 `Project_Spec.md` §5.2 Step 3 的颜色规范）
- 用户点击后：
  1. 调用 `ReviewScheduler.calculate()` 计算新状态
  2. 通过 `StudyStateDao.update()` 更新数据库
  3. 通过 `ReviewLogDao.insert()` 写入一条日志（`interactionType = 2`，记录 `costTimeMs`）
  4. 自动进入 `HorizontalPager` 下一张

**完成标准**
- 点击评分按钮后，Database Inspector 中对应 `StudyStateEntity` 的 `nextReviewTime`、`stability`、`difficulty` 均有变化
- `review_logs` 表中出现新记录
- 评分 R=1 后 `lapses` 字段 +1

---

### ✅ Task 4.6：连续评分状态迁移检查

**文件** `ReviewViewModel.kt`（新增逻辑）

**操作**
- 实现状态机迁移规则（参见 `核心数据库和算法设计.md` §2.1）：
  - `Learning`（状态 1）中连续 3 次评分 ≥ 3，自动迁入 `Reviewing`（状态 2）
  - 需要在 `StudyStateEntity` 中添加 `consecutiveGoodReviews: Int` 字段（或通过查询最近日志推算）
- 在每次 `ReviewScheduler.calculate()` 后执行此检查

**完成标准**
- 对同一公式连续评分 R=3 三次后，`learningState` 变为 2

---

### ✅ Task 4.7：考前收敛机制（Sprint Mode）

**文件** `domain/SprintModeManager.kt` 和 全局配置

**操作**
- 在全局配置（`data/AppConfig.kt` 或 DataStore）中允许设置 `targetExamDate: Long`（默认 2026 年 12 月考研日期的时间戳）
- 实现 `SprintModeManager.applyIfNeeded()` 方法：
  - 计算距考试日期剩余天数
  - 若剩余天数 ≤ 30，对数据库中所有 `stability > 15` 的记录批量执行 `S = S / 2`
  - 同时将所有 `Mastered`（状态 3）的公式 `nextReviewTime` 重置到今天（拉入复习池）
- 在 App 启动时调用一次 `SprintModeManager.applyIfNeeded()`

**完成标准**
- 将 `targetExamDate` 临时设为"当前时间 + 20天"，重启 App 后，Database Inspector 中 `stability > 15` 的记录 `stability` 值减半，`Mastered` 公式出现在今日复习队列

---

# 📝 Sprint 5：测试模块与数据统计

**目标**：无提示严格默写流程跑通，惩罚机制生效，热力图可见，顽固节点有专项处理。

---

### ✅ Task 5.1：TestScreen - 考试环境搭建

**文件** `ui/screen/TestScreen.kt` 和 `ui/viewmodel/TestViewModel.kt`

**操作**
- `TestViewModel` 从 `StudyStateDao.getMasteredFormulas()` 获取待测公式
- 进入该页面后，通过修改 `LocalWindowInsets` 或 `ImmersiveModeState` 隐藏底部 `NavigationBar`（营造考试氛围）
- 顶部显示题目（公式标题，**不显示 LaTeX**）
- 下半屏提供 `Canvas` 默写区域

**完成标准**
- 进入测试 Tab 后底部导航栏消失
- 离开后底部导航栏恢复

---

### ✅ Task 5.2：TestCanvas - 手写草稿区域

**文件** `ui/component/TestCanvas.kt`

**操作**
- 手指绘制路径，支持多笔
- 右上角悬浮两个 `IconButton`：`[撤销上一笔]` 和 `[清空画布]`（参见 `UI设计规范.md` §5 和 `Project_Spec.md` §5.3）
- 停笔后（`ACTION_UP` 无新事件超过 1.5 秒），将当前 `Bitmap` 传入 `MathOcrRecognizer.recognize()`
- OCR 返回后在 Canvas 顶部弹出最多 3 个候选 LaTeX 碎块（M3 `FilledTonalButton`），用户点击选中

**完成标准**
- 停笔 1.5 秒后，顶部出现 Mock 返回的 3 个候选项
- 撤销按钮可逐笔撤销
- 清空按钮可清空全部路径

---

### ✅ Task 5.3：严格判定与惩罚机制

**文件** `TestViewModel.kt`（新增方法）

**操作**
- 用户点击"提交核对"后，以 `AlertDialog` 展示标准答案（`MathFormulaView` 渲染）
- 仅提供两个按钮：`[完全正确]` 和 `[出现错误]`
- **`[完全正确]`**：调用 `ReviewScheduler.calculate(rating=4, isTestMode=true)` 并更新，写入日志（`interactionType = 3`）
- **`[出现错误]`**：
  1. 触发强振动（调用系统 `Vibrator`，200ms 长振）
  2. 触发屏幕边缘红光闪烁动画（`AnimatedVisibility` 或 `drawBehind`）
  3. 调用 `ReviewScheduler.calculate(rating=1, isTestMode=true)` 强制降级
  4. 写入日志（`interactionType = 3`，`lapses++`）

**完成标准**
- 点击"出现错误"后设备明显震动
- 该公式的 `learningState` 从 3 变回 1，`stability` 重置
- `lapses` 字段 +1

---

### ✅ Task 5.4：顽固节点（Leech）专项处理

> 本 Task 依赖 Task 3.1（列表高亮）和 Task 5.3（lapses 统计），需在两者完成后执行。

**文件** 涉及 `FormulaDetailScreen.kt`、`ReviewCard.kt`、`TestViewModel.kt`

**操作**（参见 `Project_Spec.md` §5.5）
1. **详情页顶部警示**：若 `lapses >= 4`，在 `FormulaDetailScreen` 顶部显示"顽固难点 ⚠️"横幅（使用 `errorContainer` 颜色）
2. **复习时关联提示**：复习 `lapses >= 4` 的公式时，`ReviewCard` 底部常驻显示 `FormulaEntity.tags` 字段内容（应用场景提示）
3. **惩罚强化震动**：复习或测试 `lapses >= 4` 的公式出错时，震动时长加倍（400ms）
4. **冲刺期降噪弹窗**：冲刺模式（Task 4.7 已激活）下，若 `lapses >= 4` 的公式再次触发遗忘，弹出 `AlertDialog`，提示"建议手动标记为暂时跳过，避免卡进度"，提供"跳过"和"继续强攻"两个选项

**完成标准**
- `lapses = 4` 的公式在列表、详情页、复习页均有视觉区分标识
- 冲刺模式 + 再次遗忘时，弹窗出现

---

### ✅ Task 5.5：学习热力图（GitHub Style）

**文件** `ui/component/HeatmapCalendar.kt`

**操作**
- 查询 `ReviewLogDao.getLogsByDateRange(365天前, 今天)`，按日期聚合统计每天的复习次数
- 使用 Compose `Canvas` 绘制 52×7 的方格热力图
- 颜色区间（4 档，基于当天复习次数）：
  - 0 次：`MaterialTheme.colorScheme.surfaceVariant`（浅灰）
  - 1~3 次：主题色 20% 不透明度
  - 4~9 次：主题色 60% 不透明度
  - 10+ 次：主题色 100% 不透明度
- 点击某个方格时，以 `Tooltip` 或 `SnackBar` 显示该天的复习条数

**完成标准**
- 热力图在 `ReviewScreen` 或独立统计页面中可见
- 有复习记录的日期格子有颜色，颜色深浅与次数成正比

---

# 🛠️ Sprint 6：自动化与最后打磨

**目标**：WorkManager 通知跑通，WebView 性能优化，UI 全局走查。

---

### ✅ Task 6.1：每日复习提醒（WorkManager）

**文件** `data/worker/DailyReminderWorker.kt`

**操作**
- 创建 `PeriodicWorkRequest`，每天早上 8:00 触发
- Worker 内查询 `getTodayReviewQueue()`，若队列非空则发送系统通知
- 通知点击后跳转到复习 Tab
- **Android 13+ 运行时权限申请**：在 `MainActivity.onCreate` 中检查 `POST_NOTIFICATIONS` 权限，若未授权则弹出权限请求对话框（首次启动时提示）

**完成标准**
- 在 Android 13+ 真机上，首次启动弹出通知权限请求
- 授权后，通过 Android Studio 的 Background Task Inspector 工具能看到 Worker 已注册
- 手动触发 Worker 执行，若有待复习公式则出现系统通知

---

### ✅ Task 6.2：WebView 复用池优化

**文件** `ui/component/WebViewPool.kt`

**操作**
- 创建 `WebViewPool` 单例，维护一个预热好的 `WebView` 对象池（容量 3）
- `MathFormulaView` 从池中取出 `WebView` 使用，使用完毕后归还池中
- 避免每次 Composable 重组都创建新的 `WebView` 实例

**完成标准**
- 在公式列表页快速滑动时，通过 Android Studio Profiler 观察内存，不出现连续的大幅内存增长（无明显内存抖动）

---

### ✅ Task 6.3：UI 全局走查清单

以下每一项单独验证，全部通过后本 Task 完成：

- [x] 所有卡片使用 `ElevatedCard` + `RoundedCornerShape(16.dp)`，无普通 `Card` 混用
- [x] 所有高频操作按钮使用 `FilledTonalButton`，非破坏性操作无实心高亮按钮
- [x] 所有页面在深色模式下对比度合理，KaTeX 公式字体在深色背景下可见
- [x] 所有页面开启 Edge-to-Edge 后，内容不被状态栏或导航栏遮挡
- [x] 底部导航栏 Tab 切换有椭圆形选中高亮效果（M3 `NavigationBarItem` 默认样式）
- [x] 页面横向切换有 `slideInHorizontally` / `slideOutHorizontally` 动画
- [x] 所有按钮点击均有水波纹（Ripple）效果，无裸 `clickable` 无反馈交互
- [x] 测试 Tab 进入/退出后底部导航栏正确隐藏/恢复
- [x] `errorContainer` 颜色仅用于错误/危险场景，不滥用

**完成标准**
- 上述 9 项全部打勾

---

# 📌 附：关键技术决策记录

> 此节记录项目中做出的关键选择，避免后续重复讨论或误解文档。

| 议题 | 决策 | 原因 |
|------|------|------|
| 公式渲染 | KaTeX + WebView（离线包） | 文档全套已基于此设计，不引入 JLatexMath |
| OCR 方案 | 接口抽象，开发期使用 MockMathRecognizer | 后期可零成本替换为 Mathpix / ML Kit / ONNX |
| 网络依赖 | 核心功能（公式、复习、测试）完全离线；OCR 作为可替换模块，云端实现为可选增强 | 兼顾离线可用性与后期扩展性 |
| 算法调度 | FSRS 变体，参数 α=0.5，D 范围 [1.0, 5.0] | 详见 `核心数据库和算法设计.md` §2.2 |
| 考试日期 | 全局配置 `targetExamDate`，默认 2026-12 | 冲刺机制依赖此配置，需在 Sprint 4.7 中实现 |
