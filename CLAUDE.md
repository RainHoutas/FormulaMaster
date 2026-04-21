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
参见 TODO.md，按 Sprint 顺序执行，当前从 Sprint 1 Task 1.1 开始

## M3 组件规范（必须遵守）
- 卡片：ElevatedCard + RoundedCornerShape(16.dp)
- 高频按钮：FilledTonalButton
- 错误/危险：MaterialTheme.colorScheme.errorContainer
- 页面过渡：slideInHorizontally + slideOutHorizontally