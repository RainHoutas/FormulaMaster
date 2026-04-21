### 🎨 Material 3 原生 UI 设计增强规范

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
- **顽固节点视觉 (Leeches)**：针对 `lapses >= 4` 的公式，其卡片背景需使用 `MaterialTheme.colorScheme.errorContainer` 予以高亮警告。