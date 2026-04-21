# 考研数学公式记忆助手 (Formula Master) - 技术规格书

## 1. 项目愿景与约束

- **目标**：开发一个纯本地运行的 Android App，通过间隔重复算法（FSRS）帮助考研学生记忆固定的数学公式。
- **核心约束**：
  - **核心功能无网络依赖**：OCR 作为可选的网络增强功能通过接口注入。
  - **非 AI 驱动**：不使用大模型生成内容，依赖确定性的数学算法。
  - **高标准 UI**：严格遵循 Material 3 设计规范。
  - **高性能渲染**：LaTeX 公式需渲染清晰且支持深色模式。

## 2. 技术栈 (Technology Stack)

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose (Material 3)
- **数据库**：Room Persistence Library (SQLite)
- **异步处理**：Kotlin Coroutines & Flow
- **渲染引擎**：KaTeX (通过本地 WebView 加载)
- **后台任务**：WorkManager (用于每日复习提醒)
- **架构模式**：严格遵循 MVVM + UDF (Unidirectional Data Flow) 单向数据流架构。
- **状态管理**：UI 状态必须封装在 `UiState` 数据类中，并通过 ViewModel 的 `StateFlow` 暴露给 Compose。严禁在 Composable 函数中直接处理复杂的业务逻辑或数据库操作。
-  **OCR 依赖倒置 (Dependency Inversion)**：针对手写公式识别，采用接口抽象设计。在 Domain 层定义 `MathOcrRecognizer` 接口（接收 Canvas 笔画，返回 Top-N 的 LaTeX 候选数组）。开发前期使用延时返回假数据的 `MockMathRecognizer` 跑通 UI 流，后期再平滑替换为 ML Kit 或自研 ONNX 模型实现，确保 UI 层与底层识别算法完全解耦。

## 3. 数据模型 (Data Schema)

### 3.1 `FormulaEntity` (静态资产)

存储公式定义的表，由 `assets/formulas.json` 预加载。

- `formulaId`: String (PK)

- `subject`: String (高数/线代/概率)

- `chapter`: String (章节名)

- `title`: String (公式名)

- `latexCode`: String (LaTeX 源码)

- `clozeData`: String (填空逻辑 JSON)

- derivationSteps: JSON 字符串：推导过程片段数组

- tags: String 应用场景标签（如：求极限、级数展开、中值定理应用）

- difficultyLevel: Int 客观难度评级 (1-5)，用于初次下发时的权重

- `clozeData` 字段 JSON 结构示例约束：

  ````json
  [
    {
      "index": 1, 
      "placeholder": "\\frac{1}{n!}", 
      "options": ["\\frac{1}{n!}", "\\frac{1}{(n+1)!}", "n!"]
    }
  ]
  ```
  ````
  
  

### 3.2 `StudyStateEntity` (核心算法状态)

存储每个公式的记忆进度。

- `formulaId`: String (PK, FK)
- `learningState`: Int (0:新, 1:学习中, 2:复习中, 3:已掌握)
- `stability`: Double (记忆稳定性，决定复习间隔)
- `difficulty`: Double (主观难度因子 1.0 - 5.0)
- `lastReviewTime`: Long (Unix 时间戳)
- `nextReviewTime`: Long (Unix 时间戳)
- `lapses`: Int (遗忘总次数)

### 3.3 `ReviewLogEntity` (流水日志)

记录每次交互，用于生成学习日历。

- `logId`: Long (PK, AutoIncrement)

- `formulaId`: String

- `reviewTime`: Long

- `userRating`: Int (1-4)

- `interactionType`: Int (记忆/复习/测试)

  **注意：此章节仅供参考，请跟据“核心数据库和算法设计.md”进行具体设计**

## 4. 核心调度算法 (FSRS 变体)

系统根据用户反馈评分 $R \in \{1: \text{忘记}, 2: \text{困难}, 3: \text{顺利}, 4: \text{极易}\}$ 更新状态：

1. **难度更新**：

   $D_{new} = D_{old} + 0.5 \times (3 - R)$ （限制在 $[1.0, 5.0]$）

2. **稳定性更新**：

   - 若 $R = 1$：$S_{new} = \min(1.0, S_{old} \times 0.2)$
   - 若 $R > 1$：$S_{new} = S_{old} \times (1 + \frac{R-1}{D_{new}})$

3. **状态流转**：

   - $S_{new} > 30$ 天时，自动标记为 `Mastered`。
   - 测试模式出错时，强制降级至 `Learning` 状态。

### 4.1 考前收敛机制 (Hard Deadline)

为了确保在 2026 年 12 月考试前所有公式都得到有效激活，算法需引入时间收敛逻辑：

1. **目标日期设定**：系统需在全局配置中允许设置 `TargetExamDate`。
2. **冲刺模式 (Sprint Mode)**：
   - 当距离考试日期 $\le 30$ 天时，触发冲刺逻辑。
   - **复习压缩**：对于所有稳定性 $S > 15$ 的公式，强制将其 $S$ 减半，确保考前至少再复习一次。
   - **全量扫描**：即使状态为 `Mastered` (3) 的公式，也会被随机抽入每日复习池，进行高频唤醒。

  ## 5. UI 与核心交互流程深度设计 (三阶段学习法)

  本应用的核心交互围绕“记忆探索”、“动态复习”和“严格测试”三个阶段展开。UI 层需严格遵守 Material 3 规范，并通过 Jetpack Compose 的状态驱动（State-driven）机制实现页面流转。

  ### 5.1 阶段一：记忆探索 (Memory & Exploration)

  - **设计目标**：帮助用户在初次接触公式时，建立精确的视觉记忆与逻辑组块，拒绝“死记硬背”。

- **UI 布局 (`MemoryScreen`)**：
  - **入口**：`LazyColumn` 分类列表，展示状态为 `New` (0) 的公式。
  - **详情视图**：沉浸式全屏 `Scaffold`。
  
- **核心交互动作**：
  - **结构化拆解 (Interactive Parsing)**：公式通过 KaTeX WebView 渲染。利用注入的 JavaScript，使公式的关键组成部分（如积分域、核心算子、指数项）成为可点击的热区 (Clickable Bounds)。用户点击某一部分时，底部通过 M3 的 `ModalBottomSheet` 向上滑出，显示该公式块的文字解析或前置条件。
  - **公式临摹 (Tracing Mode)**：提供一个“动手写”的辅助入口。在界面区域利用 Compose 的 `Canvas` 覆盖在一层浅灰色的正确公式图像之上（透明度 30%）。用户可以用手指或触控笔在屏幕上进行描红。这种触觉反馈能帮助建立对复杂数学符号（如多重积分、阶乘）的初始肌肉记忆。
  - **工具栏配套**：手写区域（Canvas）右上角必须悬浮提供一个 `IconButton`（包含清除图标），点击后瞬间清空当前 Canvas 路径，方便用户重新书写。
  - **状态激活 (Activation)**：页面底部悬浮一个宽体的 M3 `FilledTonalButton`，文案为“标记为开始学习”。点击后：
    1. 触发轻量级震动反馈 (HapticFeedback)。
    2. 数据库将其状态从 0 变更为 1 (`Learning`)，计算初始 $D$ 和 $S$ 值。
    3. 自动返回列表页，该公式在列表中显示为“复习中”的 Tag。

  ### 5.2 阶段二：遗忘复习 (Active Recall & Review)

  - **设计目标**：对抗艾宾浩斯遗忘曲线，强制大脑进行“主动回忆 (Active Recall)”，并在回忆后进行结构化强化。

    **UI 布局 (`ReviewScreen`)**：

    - **任务队列**：通过 Compose 的 `HorizontalPager` (轮播器) 或叠卡动画展示今日待复习队列（查询 `nextReviewTime <= Today` 且状态为 1 或 2 的公式）。

    **核心交互动作 (三步强化法)**：

    - **Step 1: 盲盒唤醒 (Blind Recall)**：
      - 界面中央仅显示巨大且清晰的公式标题（如“泰勒展开式”）。
      - 标题下方是一个深灰色的 `ElevatedCard` 占位遮罩，公式内容被完全隐藏。
      - 用户必须先在大脑中尝试回忆全貌，然后点击该遮罩层触发展开动画。
    - **Step 2: 填空强化 (Reinforcement via Cloze Deletion)**：
      - 遮罩层消失，但**不直接展示完整公式**。系统读取该公式的 `clozeData`，利用 KaTeX 渲染出带有关键缺失项的公式（例如将 $x^n$ 或 $1/n!$ 替换为 `[ ? ]` 占位符）。
      - 屏幕下方提供 3 到 4 个 M3 样式的候选选项卡片（包含正确项与常见的易混淆干扰项）。
      - 用户需点击正确的选项填入空缺，完成公式的逻辑拼图。
    - **Step 3: 动态自评 (Self-Assessment)**：
      - 仅当填空交互完成（无论选对选错）后，展示完整的无缺漏公式。
      - 界面底部滑出（AnimatedVisibility）四个基于 FSRS 的评分按钮：
        - `[完全忘记(1)]` (Error 红色系)
        - `[有些困难(2)]` (Warning 橙色系)
        - `[比较顺利(3)]` (Primary 科技蓝)
        - `[倒背如流(4)]` (Success 绿色系)
      - 用户结合刚才的唤醒过程和填空表现进行主观评价。点击后，算法更新参数，落库并进入下一题。

  ### 5.3 阶段三：阶段严测 (Strict Testing)

  - **设计目标**：针对已经标记为 `Mastered` (状态 3) 的公式进行硬核检验，找出“漏网之鱼”。这是一场没有提示的闭卷考试。
  - **UI 布局 (`TestScreen`)**：
    - **考试模式环境**：进入后隐藏底部导航栏 (`BottomAppBar`)，强制全屏，营造沉浸且严肃的测试氛围。
  - **核心交互动作 (无感验证)**：
    - **智能手写草稿 (Smart Scratchpad)**：
      - 界面上半部分给出题目，下半部分提供 `Canvas` 供用户默写。
      - **接口调用与候选**：用户停笔后，UI 收集笔画坐标调用 `MathOcrRecognizer.recognize()` 接口。上方瞬间渲染出接口返回的 Top-3 候选 LaTeX 公式碎块。用户点击正确的候选块，将其拼接到最终的答题区，实现“手写辅助+精准拼图”的优雅容错交互。
      - **工具栏配套**：手写区域右上角提供 `[撤销上一笔]` 和 `[清空画布]` 按钮，方便分段书写。
    - **严厉裁决 (Strict Judgment)**：
      - 用户写完后，点击“提交核对”。标准答案在顶部以 `AlertDialog` 或悬浮卡片的形式呈现。
      - 此时只有两个按钮：`[完全正确]` 和 `[出现错误]`。
      - **惩罚机制**：如果点击 `[出现错误]`，触发强烈的设备震动，屏幕边缘闪烁红光（动画）。该公式的 FSRS 稳定性 $S$ 强制清零重置，状态由 3 (`Mastered`) 打回 1 (`Learning`)，强制在明天重新进入日常复习流。如果点击完全正确，则给予极大的间隔奖励（如 3个月后才需再次测验）。

  ### 5.4 M3 全局组件规范字典 (供 AI 生成代码参考)

  为保证应用视觉高度一致，项目中各处交互必须使用以下指定的 Compose 级组件：

  - **主背景色**：`MaterialTheme.colorScheme.background` (跟随深浅色模式)。

  - **卡片容器**：一律使用 `ElevatedCard`，设置统一的 `RoundedCornerShape(16.dp)`。

  - **高频操作按钮**：使用 `FilledTonalButton` 降低视觉压迫感。

  - **危险操作/错误反馈**：使用 `MaterialTheme.colorScheme.errorContainer`。

  - **页面切换**：使用 `NavHost` 时，配置 `slideInHorizontally` 和 `slideOutHorizontally` 过渡动画。

  - **Monet 取色**：使用 `dynamicLightColorScheme`。

  - **深色模式**：注入 CSS `@media (prefers-color-scheme: dark)` 确保 KaTeX 字体在深色背景下变为浅色。

  - **沉浸式**：全屏绘制，使用 `WindowInsets` 处理状态栏。

  - **震动与触觉反馈**：  - 必须在 `AndroidManifest.xml` 中声明 `<uses-permission android:name="android.permission.VIBRATE" />`。  - 常规点击使用 Compose 自带的 `HapticFeedbackType.TextHandleMove` 等轻量级反馈；阶段三的错误惩罚需调用系统底层的 `Vibrator` 服务执行强震动。

    ### 5.5 顽固节点 (Leeches) 专项处理
    
    针对遗忘次数 `lapses >= 4` 的公式，系统需采取以下干预措施：
    
    1. **视觉区分**：
       - 在目录列表页，该公式的卡片背景使用 `MaterialTheme.colorScheme.errorContainer` 颜色。
       - 公式详情页顶部显示“顽固难点”警示标识。
    2. **交互强化**：
       - **强力反馈**：复习或测试该公式出错时，触发长频率的设备震动（Vibrator）。
       - **关联提示**：在复习此类公式时，下方自动常驻显示 `tags`（应用场景），辅助用户建立联想记忆。
    3. **降噪策略**：
       - 如果某个公式在冲刺模式下依然触发 Lapse，系统应弹窗建议用户“手动降噪”，将其标记为“暂时跳过”或“死记硬背”，避免由于极个别公式卡进度而导致整体复习断档。
    
    **UI设计细节可以参考"UI设计规范.md"来进行设计**

------

## 6. 开发路径简述 (Roadmap v2.0)

本项目采用敏捷开发模式，严格遵循“视觉与存储基建先行 -> 核心算法介入 -> 高阶功能拓展”的顺序。在执行代码生成时，须严格按照以下里程碑推进：

- **Sprint 1：基础设施与 M3 视觉定调 (基建期)**
  - 核心：配置 Material 3 动态取色（Monet）与深色模式自适应。
  - 框架：开启全局 Edge-to-Edge 沉浸式布局；封装 Navigation 路由。
  - 数据：建立 Room 数据库核心三表 (`FormulaEntity`, `StudyStateEntity`, `ReviewLogEntity`) 及相关 DAO 查询流。
- **Sprint 2：渲染引擎封装与资产预置 (内容期)**
  - 核心：封装本地 KaTeX 离线渲染组件 (`MathFormulaView`)。
  - 细节：注入深色模式自适应 CSS（背景透明、字体反色）。
  - 数据：实现 App 首次启动时的静态 JSON 公式库异步预加载入库。
- **Sprint 3：第一阶段 - 记忆模块与导航 (UI 交互)**
  - 框架：搭建全局 M3 导航结构 (`Scaffold` + `NavigationBar`)。
  - UI：使用 `LazyColumn` 实现公式分类列表与交互式详情探索页。
  - 逻辑：实现“开始学习”按钮，完成状态机初步激活（生成 State 1 记录）。
- **Sprint 4：第二阶段 - 复习模块与核心算法 (核心调度)**
  - 核心：编写纯本地 FSRS 算法调度器 (`ReviewScheduler`)，计算 $S$ 与 $D$。
  - 逻辑：实现“今日待复习”队列的 SQL 动态抓取。
  - 交互：实现“遮罩唤醒 + 4档自评”卡片，动态更新数据库实体并异步插入学习日志。
- **Sprint 5：第三阶段 - 考试模块与统计 (高阶特性)**
  - 逻辑：对已掌握 (`Mastered`) 的公式进行无提示默写抽测，实现错题的“遗忘惩罚”降级逻辑。
  - UI：提取 `ReviewLogEntity` 数据，利用 Compose `Canvas` 绘制 365 天（类似 GitHub）的学习热力图。
- **Sprint 6：自动化与最后打磨 (完善期)**
  - 拓展：接入 `WorkManager` 实现每日定时本地通知（Notification）唤醒复习。
  - 优化：优化 WebView 复用池性能，统一全站 M3 组件圆角尺寸与水波纹涟漪动效。