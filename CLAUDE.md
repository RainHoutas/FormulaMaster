# Formula Master — Claude Code 项目上下文

## 技术栈
- Kotlin + Jetpack Compose (Material 3) + Room + KaTeX WebView
- 架构：严格 MVVM + UDF 单向数据流
- 异步：Kotlin Coroutines + Flow
- 渲染：KaTeX 离线包（assets/katex/），禁止引入 JLatexMath

## 铁律（禁止违反）
- 禁止在 Composable 函数中直接操作数据库
- UI 状态必须封装在 UiState 数据类中，通过 ViewModel 的 StateFlow 暴露
- OCR 必须通过 MathOcrRecognizer 接口注入，当前实现为 MockMathRecognizer
- 禁止在 UI 层感知任何具体识别 SDK

## 方法论（用真金白银的踩坑换来的，违反必被打脸）

### 1. 涉及外部 SDK / API / 服务的能力或定价，**必须先 WebFetch 官方页**，不靠训练记忆

**踩坑案例**：
- **ML Kit Digital Ink**：训练记忆里"支持手写识别"，靠记忆推荐为 L1 本地方案 → 用户实装后第一笔下笔崩溃，因为 ML Kit **不支持数学公式**（`fromLanguageTag("zxx-Zmth")` 始终返回 null）。该方案最终被拒绝，浪费一整轮迭代。
- **Mathpix 免费层**：训练记忆里"个人开发者免费 1000 次/月"，写进用户文档 → 用户实际注册时发现需 **$19.99 一次性激活费 + 信用卡**，没有持续免费层。文档被迫重写，用户额度白白消耗。

**强制规则**：
- 写任何 API 接入代码前 → WebFetch 官方文档（端点 / 鉴权 / 请求体 / 响应字段）
- 写任何用户面向的"免费额度 / 价格"声明前 → WebFetch 官方价格页
- 文档里所有数字旁边标 "**以官方公示为准**" + 给出官方链接，便于用户自验
- 涉及**产品边界限制**（"是否支持 X"），优先做最小可执行测试，不靠文档自证

### 2. UI/渲染问题反复猜测无果时，**改用诊断驱动开发**

**踩坑案例**：公式在 WebView 中渲染异常（先是过小、后是位置错乱、再是看不见），连续 5+ 轮按"我以为的根因"修，每次都失败 + 烧用户额度。最后在 HTML 模板插入 diag 角标显示 `body.clientWidth/Height`、`window.innerWidth/Height`、`devicePixelRatio`、`computed font-size`、`scrollWidth/Height` 等运行时数据 → 用户一张截图，发现 **`body.clientHeight = 8`**（CSS `height: 100%` 在 Compose 内嵌 WebView 中失效）—— 一轮根治。

**强制规则**：
- 同一个 UI/渲染 bug 修了 2 轮没好转 → 立刻停止猜测
- 在出问题的层（HTML/CSS/JS、Compose Modifier、自定义 View 等）插入诊断输出（角标 / Toast / Logcat 都行，但要让用户**一张截图就能给出**）
- 用诊断数据反推根因，再做下一轮修复

### 3. 涉及方案选择（设置页位置 / API 默认选哪个 / UI 入口形式）必须先问用户

不要替用户做决策。给出 A/B/C 方案 + 各自利弊，等用户拍板再动手。

## 包结构
data/local/entity/   → Room Entity
data/local/dao/      → DAO 接口
domain/              → 业务逻辑（ReviewScheduler、MathOcrRecognizer 接口）
ui/screen/           → 页面级 Composable
ui/component/        → 可复用组件（MathFormulaView、TracingCanvas 等）
ui/viewmodel/        → ViewModel
ui/theme/            → Material 3 主题

## 当前进度

**阶段**：原型阶段（01）✅ + 打磨完善阶段（02）✅ 均已归档，**当前：学习流程重构阶段（03）· Sprint 1-3 全部完成并已 push**。

- **Sprint 1 ✅**：数据基础（`FormulaEntity` 9→18 字段 + `DerivationStep` 对象数组 + `FormulaSubjectMap` 多对多 + 6 类子卡 schema + 错题反向 schema + 30 公式 MVP）
- **Sprint 2 ✅**：七步学习仪式 + 复习路由器（轮转/粘卡/加强卡/默写/同日续接）+ C1 识别 / C2 加权 cloze / C3 条件先行 三卡型 + 子卡 FSRS 为唯一真相源（母卡 study_states 退役）。
- **Sprint 3 ✅**（2026-07-01 收官，代码+真机全过）：C4 推导 + C6 题型反查 + 错题本 UI 闭环（含删除快照 best-effort 还原）+ 复习 C2 判错着色 + Leech 升级（错题反向标记，全 App 统一判定 + 复习顽固 chip）。**C5 易混延后 Sprint 4**（缺 diffExplanation 内容）。364 单测全绿。
**下一个阶段**：Sprint 4（公式族图谱 + 学习阶段切换 + C5 易混辨析）。开工前扫改进点池排 Task + 过二级决策。
> 当前架构现状详见 [`docs/design/架构总览.md`](docs/design/架构总览.md)（新对话对接现状的权威入口）。

## 🚨 新对话开工前必读（强制顺序）

按顺序读取以下文件，缺一不可：

1. **`TODO.md`**（项目根）— 指针文件，导航到当前阶段
2. **`docs/design/架构总览.md`** 🔥 — **当前系统架构 + 状态全景**，新对话对接现状的第一入口（防止读到原型期过时信息）
3. **`docs/planning/RFC_学习流程重构.md`** — **当前阶段设计底稿**，所有 Sprint 决策来源
4. **`docs/phases/03_学习流程重构_TODO.md`** — 阶段 Task 清单、Done 标准、已完成记录
5. **`docs/planning/改进点池.md`** — 新想法集散地、分类标签、已拒绝/搁置决策

> 文档库结构见 [`docs/design/架构总览.md`](docs/design/架构总览.md) 末尾「文档地图」；`docs/phases/01·02` 为**冻结的历史归档**，其中对旧文档名/章节的引用是当时的记录，勿据此对接现状。
> auto-memory 索引（不放项目内）：`~/.claude/projects/-home-houtas-StudioProjects-FormulaMaster/memory/MEMORY.md`，
> 含 `HANDOFF_TO_NEXT_SESSION.md`（对话模式约定 + 踩坑清单累积）和 `project_progress.md`（开发进度快照）。

读完这几个文件，你就知道：
- 当前该做什么（Sprint 3 已完成，下一步 Sprint 4）
- 用户冒出新想法时往哪里写（改进点池"待评估"分区，**不打断当前 Sprint**）
- 用户说"开新 Sprint"时怎么规划（扫改进点池 → 按 `(优先级 ASC, 时间 ASC)` 排序 → 取 TOP 4-8 询问确认）
- 哪些决策已经做过（L2 自训练 / LaTeX 输入法 / Test 移出 NavBar 都已搁置或拒绝）

**铁律**：涉及方案选择（如设置页位置、API 默认选哪个）必须先问用户再动手，不要替用户做决策。

## 🛠️ 工具：Android CLI（优先使用）

本机已装好 `android` 命令（位置：系统 PATH）。**优先用它替代手敲 `./gradlew installDebug` + adb**：

| 子命令 | 用途 |
|------|------|
| `android run` | 一键编译 + 部署到当前设备/模拟器 |
| `android emulator` | 列出 / 启动 / 关闭 AVD |
| `android layout` | 输出当前 App 的布局树（Compose UI 调试，替代部分诊断角标） |
| `android screen` | 截屏 / 录屏（替代用户手动截图反馈） |
| `android describe` | 分析项目元数据（manifest / gradle 配置摘要） |
| `android sdk` | 列出已装 / 待装 SDK 组件 |
| `android docs` | 离线查 Android API 文档 |

注意：网络隔离环境下首次运行会下载内嵌资源，需用户确认；运行时默认收集匿名指标，可加 `--no-metrics` 关闭。

## M3 组件规范（必须遵守）
- 卡片：ElevatedCard + RoundedCornerShape(16.dp)
- 高频按钮：FilledTonalButton
- 错误/危险：MaterialTheme.colorScheme.errorContainer
- 页面过渡：slideInHorizontally + slideOutHorizontally