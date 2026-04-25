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

## 包结构
data/local/entity/   → Room Entity
data/local/dao/      → DAO 接口
domain/              → 业务逻辑（ReviewScheduler、MathOcrRecognizer 接口）
ui/screen/           → 页面级 Composable
ui/component/        → 可复用组件（MathFormulaView、TracingCanvas 等）
ui/viewmodel/        → ViewModel
ui/theme/            → Material 3 主题

## 当前进度

**阶段**：原型阶段已完成（2026-04-24 收尾），**当前进行中：打磨完善阶段 · Sprint 1**（手写识别真实落地）。

## 🚨 新对话开工前必读（强制顺序）

按顺序读取以下文件，缺一不可：

1. **`memory/HANDOFF_TO_NEXT_SESSION.md`** — 对话模式约定、工作流、项目定位、Sprint 1 速览、踩坑清单
2. **`docs/phases/02_打磨阶段_TODO.md`** — 当前 Sprint 的 Task 清单与 Done 标准
3. **`docs/改进点池.md`** — 新想法集散地、分类标签、已拒绝决策（含 L2 搁置原因）
4. **`TODO.md`**（项目根） — 指针文件，确认文档结构

读完这四个文件，你就知道：
- 当前该做什么（Sprint 1 Task 列表）
- 用户冒出新想法时往哪里写（改进点池"待评估"分区，**不打断当前 Sprint**）
- 用户说"开新 Sprint"时怎么规划（扫改进点池 → 按 `(优先级 ASC, 时间 ASC)` 排序 → 取 TOP 4-8 询问确认）
- 哪些决策已经做过（L2 自训练已搁置、三路识别器并列、用户自填 API Key）

**铁律**：涉及方案选择（如设置页位置、API 默认选哪个）必须先问用户再动手，不要替用户做决策。

## M3 组件规范（必须遵守）
- 卡片：ElevatedCard + RoundedCornerShape(16.dp)
- 高频按钮：FilledTonalButton
- 错误/危险：MaterialTheme.colorScheme.errorContainer
- 页面过渡：slideInHorizontally + slideOutHorizontally