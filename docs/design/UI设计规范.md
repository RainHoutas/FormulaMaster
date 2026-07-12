### 🎨 Material 3 原生 UI 设计增强规范

> **本文档反映现状**（学习流程重构阶段）。下列 M3 原则持续有效；页面清单见 §6。
>
> **当前页面 / 组件清单**（对接现状用）：
> - **四 Tab**：记忆 `MemoryScreen`（含错题本 FAB + leech 红卡）/ 复习 `RouterReviewScreen` / 测试 `TestScreen` / 设置 `SettingsScreen`
> - **学习**：`FormulaDetailScreen`（信息展示 + leech 横幅）→ `FormulaLearnRitualScreen`（七步编码仪式）
> - **复习卡面板**（`RouterReviewScreen` 内）：C1RecognitionPane / C2ClozePane / C3PreconditionPane / C4DerivationPane / C6TypicalProblemPane / 通用 ShowCardPane；卡顶 `CardHeaderChips`（卡型 + 强标记 + 加强卡回考 + **🔥 顽固**）
> - **错题本**：`ErrorBookScreen`（列表 ↔ 新增表单，全 chip / 数字键盘 / 公式多选池）
> - **复用组件**：`MathFormulaView`（KaTeX/WebView）/ `LatexChipsView`（KaTeX 选项 chip）/ `TracingCanvas`（临摹默写）

#### 0. UI 架构模式约束 (MVVM + UDF)
- 严格遵循 MVVM + UDF (单向数据流) 架构。
- 严禁在 Composable 函数中直接进行数据库读写操作。所有 UI 状态必须封装在 `UiState` 数据类中，通过 ViewModel 的 `StateFlow` 暴露给 Compose，事件通过回调函数 (Lambda) 向上传递给 ViewModel 处理。
- **依赖倒置 (Dependency Inversion)**：底层硬件与算法（如手写识别）必须抽象为接口。例如，手写 OCR 功能必须在 Domain 层定义 `MathOcrRecognizer` 接口，UI 层仅依赖接口和其返回的 Top-N 候选数据，严禁在 UI 组件中直接强耦合具体的识别 SDK。

#### 1. 莫奈动态取色 (Dynamic Color) & 深色模式自适应

Compose 已经为你铺好了路。在 `ui/theme/Theme.kt` 中，你可以通过简单的逻辑让 APP 完全跟随系统的深色模式和壁纸颜色：

- **实现要点：**
  - 通过 `isSystemInDarkTheme()` 自动判断深浅色。
  - 在 Android 12 (API 31) 及以上设备，使用 `dynamicLightColorScheme(context)` 和 `dynamicDarkColorScheme(context)` 提取系统壁纸的主题色。
  - 在低版本设备上，提供一套自己设计的后备（Fallback）色彩方案（比如干净的极客蓝或护眼绿）。

#### 2. 全屏沉浸式体验 (Edge-to-Edge)

原生安卓的精髓在于让内容延伸到状态栏和导航栏下方，不要留出突兀的黑边或白边。

- **实现要点：** 在 `MainActivity.kt` 的 `onCreate` 中，调用 `enableEdgeToEdge()`。然后在 Compose 的 `Scaffold` 中正确处理 `WindowInsets`，让你的公式卡片能够在滑动时优雅地穿过系统状态栏的底层。

#### 3. M3 核心组件的替换与应用

在设计那三个核心 Tab（记忆、复习、测试）时，严格使用 M3 的语义化组件：

- **底部导航：** 使用 M3 的 `NavigationBar` 和 `NavigationBarItem`，它自带椭圆形的选中高亮胶囊效果。
- **公式卡片：** 放弃普通的阴影，使用 M3 的 `ElevatedCard` 或带边框的 `OutlinedCard`，配合大圆角（如 `RoundedCornerShape(16.dp)`），看起来会非常清爽。
- **交互按钮：** 填空或自评时，使用 `TonalButton` 或 `FilledTonalButton`，这种按钮的视觉层级比实心按钮低，非常适合高频的“复习评价”操作，不会喧宾夺主。

#### 3.1 M3 官方组件规范清单（Sprint 4 补 · 来源 `developer.android.com` 官方）

> ⚠️ **涉及 M3 具体规范先查官方文档**（`m3.material.io` 是 JS 单页站，WebFetch 抓不到内容 —— 已踩坑；改用 `developer.android.com/develop/ui/compose/components/*`）。新页面 / chrome 一律对照此清单，勿手搓；手搓的自定义视觉（如图谱画布）也须沿用同一套**色板 / 圆角 / 触达 / tonal 层级**。

- **顶栏**：优先 `CenterAlignedTopAppBar`（**标题居中 + `navigationIcon` 左置**，天然满足"返回与标题分离、标题居中"）/ `TopAppBar`（小）/ `MediumTopAppBar` / `LargeTopAppBar`。色走 `TopAppBarDefaults.topAppBarColors(containerColor = surface 系, titleContentColor = onSurface/primary)`；tonal elevation 组件自动；滚动行为 `enterAlwaysScrollBehavior` / `pinnedScrollBehavior` 挂 `Scaffold` 的 `nestedScroll`。浮动 chrome（如图谱子层的胶囊标题栏）不走标准 TopAppBar 时，也须用同套色板。
- **颜色走语义角色，禁硬编码**：背景 / 层级用 `surface` / `surfaceContainerLow(est) / High(est)`（**tonal 表达层级，非纯阴影**）；强调 `primary` / `primaryContainer`；文字 `onSurface` / `onSurfaceVariant`。⚠️ **数据可视化配色例外**：图谱学科分类色（高数蓝 / 线代赭 / 概率绿）、关系边三色（推导 / 易混 / 同族）、节点状态色（未学 / 学习中 / 已掌握 / 顽固）属数据色，可硬编码但**须注释「数据色，非主题色」**。
- **图标按钮**：`IconButton` / `FilledTonalIconButton` / `FilledIconButton`（触达 ≥ 48dp）。
- **按钮层级**：`Button`(filled) > `FilledTonalButton` > `OutlinedButton` > `TextButton`；高频 / 次要操作用 tonal。
- **卡片**：`ElevatedCard` / `OutlinedCard` + `RoundedCornerShape(12–16.dp)`。
- **FAB**：`FloatingActionButton`（`containerColor = primaryContainer`），bottom-end padding 16.dp（叠加系统栏 inset）。
- **进度**：`LinearProgressIndicator(progress = { … })` / `CircularProgressIndicator`。
- **触达 / 间距**：可点元素 ≥ 48dp；布局用 `Arrangement.spacedBy` + `gap`，勿零散 margin。
- **边到边**：`enableEdgeToEdge` + Scaffold `innerPadding` → `contentPadding`；chrome / 边框须**内缩系统栏 inset + 屏幕圆角余量**（图谱子层的内缩圆角边框即此处理，避免被刘海 / 导航栏 / 屏幕圆角吃掉）。

> 官方 skill 备用：`.claude/skills/` 已装 `adaptive`（大屏 / 折叠适配）、`edge-to-edge`（沉浸式 inset）、`styles`（Compose Styles API 统一组件样式）—— 做全局 UI 打磨时优先调用。

#### 4. ⚠️ 终极避坑：KaTeX 渲染与深色模式的冲突

这是很多开发者会忽略的盲点！Compose 的 UI 会自动变黑，但 `WebView` 里加载的 KaTeX 公式 HTML 默认背景是白色的，字是黑色的。

- **解决方案：** 你需要在加载 WebView 数据时，将当前的深浅色状态传给 HTML。通过注入简单的 CSS 来反转公式颜色：

  CSS

  ```
  @media (prefers-color-scheme: dark) {
      body { background-color: transparent; color: #E3E3E3; }
  }
  ```

  这样才能保证在深色模式下，背景透明，公式变成白色，完美融入 M3 的深色背景中。
  
  

#### 5. 交互外设与辅助功能
- **震动反馈 (Haptic)**：应用中存在强弱不同的触觉反馈。必须在 `AndroidManifest.xml` 中声明 `<uses-permission android:name="android.permission.VIBRATE" />`。错误惩罚（如考试阶段选错）需调用系统 `Vibrator` 执行强震，普通点击使用 Compose 的 `HapticFeedbackType`。
- **智能草稿交互 (Smart Canvas)**：在提供 `Canvas` 默写区域的界面，必须包含两个核心交互：
  1. 界面右上角提供 `[撤销上一笔]` 和 `[清空画布]` 的悬浮 `IconButton`。
  2. 停笔后自动调用识别接口，并在 Canvas 顶部弹出 Top-3 候选 LaTeX 公式碎块的 UI 容器，用户点击碎块即可完成拼图填空。
- **顽固节点视觉 (Leeches)**：leech 判定统一走 `domain/LeechDetector`（`lapses ≥ 4` **或** 近 7 日被错题反向标记 ≥ 2 次），**全 App 一致**——记忆卡背景用 `errorContainer` 红底、详情页 leech 横幅、复习卡顶「🔥 顽固」chip、测试答错加强震动。⚠️ 勿再在 UI 内联写 `lapses >= 4` 魔数，一律调 `LeechDetector`。

#### 6. 页面清单
见本文档顶部「当前页面 / 组件清单」。新增页面时保持 M3 语义化组件 + 大圆角 `ElevatedCard` + `FilledTonalButton` 高频操作 + 页面过渡 `slideInHorizontally`/`slideOutHorizontally` 的一致性。