# AGENTS.md — Formula Master 项目权威说明（任意 AI 助手入口）

> 这是本项目**工具无关的权威说明书**。无论你是 Claude Code / Cursor / Copilot / 其它 AI，
> 打开本仓库**先完整读本文件**，再按下方「开工必读」补齐上下文。
> 各工具的专用文件（`CLAUDE.md` / `.cursorrules` / `.github/copilot-instructions.md`）只是**指向这里的瘦指针**，
> 正文统一维护在本文件 + `docs/`，避免多份漂移。

---

## 1. 项目概况

**Formula Master** = 面向中国**考研数学**的**公式记忆 App**（Android 原生）。

- **它解决什么**：考研数学几百个公式，靠死记硬背记不牢、不会用。本 App 用**认知科学 + 间隔重复（FSRS）**，
  把每个公式拆成"编码学习 → 多维复习"两段，让用户从**认得出 / 填得对 / 知道条件 / 推得出 / 不混淆 / 会选用**
  六个角度真正掌握，而非背长相。
- **长期愿景**：做成领域无关的记忆「外壳」（将来可插入初高中数学、英语单词等"词典"）——数据层已按
  **标签化 + 原子化**打好地基（见 §5）。但**⚠️ 眼下一切以考研数学核心功能为准，不偏移愿景**。
- **用户**：个人开发者单干（暑期高强度开发），真机 = 一台 Android 手机（有线/无线 adb）。
- **商业化 / 开源策略**（用户 2026-07-13 明确方向，**具体方案尚未构思**，勿当既定事实引用）：
  - **目前**：仍以**功能完善**为开发目标，不铺商业化。
  - **后期开发阶段**：**积极拥抱开源**（框架 / 设计开源），**部分功能或服务商业化**（freemium 方向）。
  - **开源目的**：让社区更好地阅读 / 了解 / 优化（大量代码由 AI 生成，开源利于协作打磨）。
  - **⚠️ 待解问题**：开源的同时要**想办法防止开发成果被偷窃**（开源可读 vs 防盗的平衡，到时再设计）。
  - **付费功能初步构思（未定，仅方向）**：联网账号、打卡记录、更新版本维护公式库等；手写识别拟**保持免费**。

## 2. 当前现状（交接第一要看 · 更新即改）

> **阶段**：原型阶段（01）✅归档 · 打磨完善阶段（02）✅归档 · **学习流程重构阶段（03）= 当前**。
> **进度**：03 阶段 **Sprint 1-5 主干完成并已 push**；**Sprint 6 收尾进行中（欠 7 块债）**。
> **代码体量信号**：单测 **429 全绿**；Room DB **version 12**；30 公式 MVP 种子。

**Sprint 1-5 主干（已 push）**：
- S1 数据基础：`FormulaEntity` 多字段 + `DerivationStep` + 6 类子卡 schema + 错题反向 + 30 公式
- S2 七步学习仪式 + 复习路由器（轮转/粘卡/加强卡/默写/同日续接）+ C1/C2/C3 卡型 + 子卡 FSRS 为唯一真相源
- S3 C4 推导 + C6 题型反查 + 错题本 UI 闭环 + C2 判错着色 + Leech 升级（全 App 统一判定）
- S4 数据层地基**标签化**（tags/entry_tag_map/entry_relations 三表，退休 formula_subject_map）+ **公式族图谱=记忆主视图**
- S5 StudyPhase 学习阶段切换（一/二/三轮/冲刺/保持 + 新卡上限 + intervalFactor + 交错策略）

**🔧 Sprint 6 收尾 = 当前欠的 7 块债**（逐条现状/还差/验收见 [`TODO.md`](TODO.md) § Sprint 6 + [`docs/phases/03_学习流程重构_TODO.md`](docs/phases/03_学习流程重构_TODO.md)）：
1. 🔴 **C5 易混辨析卡整卡**（只有纯逻辑代码半 `DiscriminationCardBuilder`；无面板、复习中被剔除；缺 `diffExplanation` 内容）
2. 🔴 **七步 Step 2 拆块讲解**（占位；缺 `chunk` 字段）
3. 🔴 **七步 Step 5 Worked Example**（占位；缺 `workedExamples` 字段）
4. 🟡 **七步 Step 7 迷你卡 C4/C5/C6 形态**（现"自动通过"）
5. 🟡 **#323 毙掉项隐藏**（代码半 `LearningItemVisibility`；缺 `excludedItems` 列 + v13 迁移 + 三态渲染）
6. 🟡 **StudyPhase 真机验收**（设置切换已验；新卡上限/交错未真机）
7. 🔴 **复习默写环节**（MVP：恒显答案自评；hint 分级渐进揭示未实装、未接手写/纸笔输入）

> **6 张子卡定义**：C1 识别(名→式) / C2 加权 Cloze(式→部件) / C3 条件先行(条件→式) / C4 推导(推导→式) /
> C5 易混辨析(式↔式，N选1) / C6 题型反查(题型→式)。**C1/C2/C3/C4/C6 已落地，C5 只有代码半。**
> **学习 vs 复习**：七步仪式 = 学习端（首次激活一次性走完）；6 类子卡 = 复习端（各自 FSRS，路由器长期调度）。
> 衔接点 = 七步 Step 7 巩固迷你卡。

## 3. 开工必读（补齐上下文，按顺序）

1. **本文件** — 项目权威说明
2. [`docs/ai-context/协作约定.md`](docs/ai-context/协作约定.md) — **怎么跟这个用户干活**（沟通/决策/测试分工/产品铁律）🔥
3. [`TODO.md`](TODO.md) — 阶段结构全景 + Sprint 6 欠债清单
4. [`docs/design/架构总览.md`](docs/design/架构总览.md) — **当前系统架构 + 状态全景**（对接现状权威，防读到原型期过时信息）
5. [`docs/planning/RFC_学习流程重构.md`](docs/planning/RFC_学习流程重构.md) — 当前阶段设计底稿（所有决策来源）
6. [`docs/planning/改进点池.md`](docs/planning/改进点池.md) — 新想法集散 + 已拒绝/搁置决策（L2 自训练 / LaTeX 输入法 / Test 移出 NavBar 均已搁置）
7. [`docs/ai-context/环境与工具.md`](docs/ai-context/环境与工具.md) — `android` CLI / 已装 skills / 真机 DB pull-modify-push

> 新想法冒出 → 写进改进点池"待评估"，**不打断当前 Sprint**。
> 用户说"开新 Sprint" → 扫改进点池按 (优先级 ASC, 时间 ASC) 取 TOP 4-8 询问确认。
> `docs/phases/01·02` 是**冻结历史归档**，勿据此对接现状。

## 4. 技术栈

- Kotlin + Jetpack Compose (Material 3) + Room + KaTeX WebView
- 架构：**严格 MVVM + UDF 单向数据流**
- 异步：Kotlin Coroutines + Flow
- 渲染：**KaTeX 离线包**（`assets/katex/`），**禁止引入 JLatexMath**

## 5. 架构铁律（禁止违反）

- **禁止在 Composable 函数中直接操作数据库**
- UI 状态必须封装在 **UiState 数据类**中，通过 ViewModel 的 **StateFlow** 暴露
- OCR 必须通过 **`MathOcrRecognizer` 接口注入**（当前实现 `MockMathRecognizer`）；**禁止在 UI 层感知任何具体识别 SDK**
- 分类/关系层已**标签化**：`tags`(namespace:value) + `entry_tag_map`(带 isPrimary) + `entry_relations`(有向 DERIVATION / 无向 CONFUSABLE·SIBLING)；
  `subject`/`chapter` 保留为**种子期写一次的显示缓存**（路径 2，非真相源）

## 6. 包结构

```
data/local/entity/   → Room Entity
data/local/dao/      → DAO 接口
domain/              → 业务逻辑（ReviewScheduler / MathOcrRecognizer 接口 / 各卡型纯逻辑构造器）
ui/screen/           → 页面级 Composable
ui/component/        → 可复用组件（MathFormulaView / TracingCanvas / LatexChipsView 等）
ui/viewmodel/        → ViewModel
ui/theme/            → Material 3 主题
```

## 7. 方法论（真金白银踩坑换来的，违反必被打脸）

### 7.1 外部 SDK/API/服务的能力或定价，**必须先 WebFetch 官方页**，不靠训练记忆
**踩坑案例**：
- **ML Kit Digital Ink**：靠记忆当"支持手写识别"推荐为本地方案 → 实装第一笔崩溃，因它**不支持数学公式**（`fromLanguageTag("zxx-Zmth")` 恒返回 null），整轮迭代作废。
- **Mathpix 免费层**：记忆里"个人免费 1000 次/月"写进文档 → 实际需 **$19.99 激活费 + 信用卡**，无持续免费层，文档被迫重写。

**强制**：写接入代码前 WebFetch 官方文档（端点/鉴权/请求体/响应）；写"免费额度/价格"声明前 WebFetch 官方价格页，数字旁标"以官方公示为准"+链接；产品边界（"是否支持 X"）优先做最小可执行测试。

### 7.2 UI/渲染问题反复猜测无果 → **改用诊断驱动开发**
**踩坑案例**：公式在 WebView 渲染异常，连 5+ 轮按"我以为的根因"修全失败。最后在 HTML 模板插 diag 角标显示 `body.clientWidth/Height`、`devicePixelRatio`、`computed font-size`、`scrollHeight` 等运行时数据 → 用户一张截图见 **`body.clientHeight = 8`**（CSS `height:100%` 在内嵌 WebView 失效）→ 一轮根治。

**强制**：同一 UI/渲染 bug 修 2 轮没好转 → 立刻停止猜测，在出问题的层（HTML/CSS/JS、Compose Modifier、自定义 View）插诊断输出（角标/Toast/Logcat，让用户**一张截图就能给**），用数据反推根因。

### 7.3 涉及方案选择必须先问用户
见 [`docs/ai-context/协作约定.md`](docs/ai-context/协作约定.md) B1（这是协作行为规则，与上面两条技术方法论互补）。

## 8. M3 组件规范（必须遵守）

- 卡片：`ElevatedCard` + `RoundedCornerShape(16.dp)`
- 高频按钮：`FilledTonalButton`
- 错误/危险：`MaterialTheme.colorScheme.errorContainer`
- 页面过渡：`slideInHorizontally` + `slideOutHorizontally`
- 完整官方 M3 组件清单见 `docs/design/UI设计规范.md §3.1`

---

## 维护约定（防漂移）

- 本文件是**技术/架构/现状的唯一真相源**；协作行为规则真相源在 `docs/ai-context/协作约定.md`；工具/环境在 `docs/ai-context/环境与工具.md`。三者互不重复、互相指向。
- **§2 当前现状**随每个 Sprint / 重大进度变化即时更新（别让它像旧 CLAUDE.md 那样停在 Sprint 1-3）。
- 换 AI 工具的完整交接包 = 本文件 + `docs/ai-context/` + `TODO.md` + `docs/`，不依赖任何工具私有存储。
