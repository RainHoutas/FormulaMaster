---
name: Formula Master — 打磨完善阶段 TODO
description: 原型之后的精细化开发，Sprint 从改进点池滚动生成
created: 2026-04-24
parentPhase: 01_原型阶段（已归档）
status: 进行中
---

# 打磨完善阶段

> 本阶段承接[原型阶段](01_原型阶段_TODO.md)，把 Mock 实现替换为真实可用，把"够用"的 UI 精修到"想用"。
> 与原型阶段不同，本阶段 Sprint 的内容由 [`../改进点池.md`](../改进点池.md) 滚动生成。

---

## 阶段目标

1. **识别真实化**：Mock 手写识别 → 国内外 OCR API（用户可选并独立绑定 Light / Deep 两档）
2. **交互精细化**：Palm Rejection、实时预览、双档识别、长公式自适应
3. **学习流程扩展**：临摹/复习/测试之外，引入更多学习方式（按改进点池生长）
4. **工程完善**：设置页、API Key 加密存储、识别失败反馈、错误日志

## 阶段原则

- 单 Sprint Task 数 ≤ 8，便于收敛
- Sprint 完成必须满足：编译通过 + 关键 Task 打勾 + 改进点池状态同步
- 新想法**不打断当前 Sprint**（除非 P0 阻塞），等下轮规划再纳入
- 不预支投入产出比不明的大工程（例如自训练 ML 模型），用真实数据触发决策

## 架构铁律（与原型阶段一致）

- 严格 MVVM + UDF，禁止在 Composable 中直接操作数据库
- OCR 必须通过 `MathOcrRecognizer` 接口注入，UI 层禁止感知具体实现
- 渲染引擎统一使用 KaTeX + WebView

---

## Sprint 1：手写识别真实落地（2026-04-24 ~ 2026-04-29）

### 背景
原型阶段 `MockMathRecognizer` 始终返回固定候选，无法体现真实默写体验。本 Sprint 落地两路云端 API 识别方案：
- **A1**：Mathpix Snip（海外 API，用户自填 AppID + AppKey）—— 准确率最强，免费 1000 次/月
- **A2**：SimpleTex（国内 API，用户自填 Token）—— 国内直连，免费 1000 次/天

> **注**：原计划的 L1 ML Kit Digital Ink 已拒绝（Task 1.2），ML Kit 不支持数学公式识别（`fromLanguageTag("zxx-Zmth")` 始终返回 null）。
> 端侧本地方案改为长期项 L2（端侧 TFLite/ONNX，CROHME 数据集训练），在改进点池中作为 P3 项。

项目为个人自用 + 未来开源，不做商业化考量。用户在设置中自行配置 Key 并绑定到 Light / Deep 两档。

### Task 列表

- [x] **Task 1.1** 手写交互升级 ✅ 2026-04-24
  - 加入 Palm Rejection（过滤 `TOOL_TYPE_PALM`）
  - 双档 debounce：300ms 轻量识别 + 1500ms 深度识别
  - 顶部实时 LaTeX 预览条（MathFormulaView，40dp 高）
  - 预览条右侧"采纳 ✓"快捷按钮
  - 画布横向自适应 + 右侧 fade 溢出提示
  - **Done 标准**：TestCanvas 编译通过，手指+手掌同时按下不混乱，短公式停笔 300ms 见预览
  - **实现要点**：
    - 接口 `MathOcrRecognizer.recognize(input, mode)` 扩展了 `RecognitionMode` 参数（Light/Deep），
      采用 B2 方案为未来 ML Kit / API 实现预留差异化策略空间
    - Palm Rejection 用 `pointerInteropFilter` + `MotionEvent.getToolType`，
      `TOOL_TYPE_PALM`（API 33+）硬编码值 `5` 避开 stub 解析问题，低版本回退到原行为
    - 画布占满容器宽度（fillMaxSize），不嵌套 horizontalScroll
    - 候选区抽成私有 `CandidatesOverlay` 函数以隔离外层 scope，避免 `AnimatedVisibility`
      嵌套 `Column` → `Box` 时的 ColumnScope 歧义报错
    - 右侧 24dp 横向渐变 Box 作为视觉装饰
  - **现场修复（2026-04-25）**：
    - **症状**：手指按下立即断笔 + 画板抖动
    - **根因**：父级 `Box.horizontalScroll(scrollState)` 的 scrollable modifier 在水平
      滑动距离超过 touch slop 时拦截事件，子级 `pointerInteropFilter` 只收到
      ACTION_DOWN 然后 ACTION_CANCEL；同时动态画布宽度变化触发 layout 重排
    - **修复**：移除 horizontalScroll、动态画布宽度、自动滚动跟随逻辑；
      画布固定 = 容器尺寸。默写场景一次写一个短公式即采纳/清空，不需要无限横滚

- [~] **Task 1.2** MlKitInkRecognizer 接入（L1）❌ 已拒绝 2026-04-25
  - **拒绝原因**：ML Kit Digital Ink Recognition **不支持数学公式**识别。
    `DigitalInkRecognitionModelIdentifier.fromLanguageTag("zxx-Zmth")` 在所有版本均返回 null，
    官方支持的模型仅限自然语言文字、手势、Emoji、Autodraw 形状，不含任何数学符号。
    真机测试确认，第一笔下笔即 `IllegalStateException`。
  - **替代路线**：
    - 短期（当前 Sprint）：L1 槽位搁置，只保留 A1（Mathpix）和 A2（SimpleTex）API 方案
    - 长期：端侧 TFLite/ONNX 方案（CROHME 数据集 + 笔画时序 LSTM/TCN，聚焦考研符号集）
      已作为独立改进点加入改进点池，待有 ML 精力时专项推进

- [x] **Task 1.3** LaTeX 规范化后处理器 ✅ 2026-04-25
  - 新建 `domain/LatexNormalizer.kt`（`object` 单例，无外部依赖）
  - 四步流水线：Unicode→LaTeX命令 / 函数名补全 / 花括号补齐 / 空格清理
  - 内置考研常用符号映射（50+ 个 Unicode 符号）+ 函数名列表（23 个，长优先匹配）
  - `isLikelyFormula()` 过滤纯中文/空白候选；`normalizeAndFilter()` 批量化+去重
  - **Done 标准**：35 条单元测试全部通过（覆盖 Unicode 转换、函数名规范化、括号补齐、过滤、幂等性）

- [x] **Task 1.4** MathpixApiRecognizer 接入（A1）✅ 2026-04-25
  - 新建 `domain/MathpixApiRecognizer.kt`：构造器接受 appId + appKey（Key 来源由 Task 1.6 接入）
  - 接入栈：Retrofit 2.11 + OkHttp 4.12 + Gson Converter，15s 三档超时
  - **API 契约严格对齐 https://docs.mathpix.com/reference/post-v3-text**：
    - 端点：POST `https://api.mathpix.com/v3/text`
    - 请求 `formats: ["latex_styled"]` —— 注意 `"latex"` 不是合法值
    - 响应主输出在顶层 `latex_styled` 字段（不是 `data[]` 数组），`data[]` 单公式响应中常缺失
  - 输入处理：
    - `OcrInput.BitmapInput`：base64 编码后直传
    - `OcrInput.StrokeInput`：白底黑线 PNG，**自动 upscale 到至少 800px 宽**（OCR 准确率优化）
      笔画粗细同步缩放，使用 ROUND cap/join 提高线段连贯性
  - 请求格式：base64 data URL（`data:image/png;base64,…`），Content-Type 由 GsonConverter 自动设
  - Header 鉴权：`app_id` / `app_key`
  - **架构清洁**：响应解析提取为纯函数 `MathpixApiRecognizer.extractCandidates()`，
    无副作用（log 留在调用方），便于 JVM 单元测试验证 JSON 契约
  - 错误兜底（多层防护，绝不向上抛）：
    - 顶层 `error` 字段非空（200 但业务错误，如"图像太小"）→ 返回空列表 + Logcat
    - `HttpException`（含 401 Key 无效 / 500 服务异常）→ 返回空 + Logcat
    - `SocketTimeoutException`（超时）→ 返回空 + Logcat
    - `UnknownHostException`（无网络）→ 返回空 + Logcat
    - 通用 `Exception` → 返回空 + Logcat
    - Key 缺失（空字符串）本地短路，不发起请求
  - 已添加 `INTERNET` + `ACCESS_NETWORK_STATE` 权限到 AndroidManifest
  - **Done 标准**：BUILD SUCCESSFUL；**12 条单元测试通过**（请求契约 2 条、响应契约 5 条、
    错误处理 2 条、复杂 LaTeX 透传 2 条、构造器 2 条），覆盖 Mathpix v3/text JSON 契约的解析/序列化正确性。
    Bitmap 渲染路径需 Robolectric/androidTest（JVM 单测无 Android framework），
    端到端验收待 Task 1.7 SettingsScreen 完成后真机填入有效 Key 完成

- [x] **Task 1.5** SimpleTexApiRecognizer 接入（A2）✅ 2026-04-25
  - 新建 `domain/SimpleTexApiRecognizer.kt`：构造器接受 token（UAT）
  - **API 契约严格对齐 https://doc.simpletex.cn/zh/api/api_formula_recognition.html**：
    - 端点：POST `https://server.simpletex.net/api/latex_ocr`（标准模型）
    - 鉴权：单 header `token: <UAT>`（无 Bearer 前缀）
    - 请求：**multipart/form-data**，字段名 `file`，PNG 二进制（与 Mathpix 的 base64 JSON 路径根本不同）
    - 响应：嵌套对象 `res.latex`（**注意不是顶层字段**），`status: false` 时无候选
  - 共享渲染：抽出 `domain/util/StrokeBitmapRenderer.kt`，A1/A2 共用，未来 L2 也可复用
  - **架构清洁**：响应解析提取为纯函数 `SimpleTexApiRecognizer.extractCandidates()`，
    无副作用，便于 JVM 单元测试验证 JSON 契约
  - 错误兜底（与 A1 同构）：
    - `status: false` → 空列表 + Logcat（含 message 字段说明）
    - `HttpException` / `SocketTimeoutException` / `UnknownHostException` / 通用 `Exception` 全 catch
    - Token 缺失（空字符串）本地短路，不发起请求
  - **Done 标准**：BUILD SUCCESSFUL；**13 条单元测试通过**（成功路径 2、status=false 处理 2、
    边界 4、复杂 LaTeX 透传 2、空响应防 NPE 1、构造器 2），覆盖 SimpleTex 嵌套 res.latex JSON 契约。
    端到端验收待 Task 1.7 SettingsScreen 真机填入有效 UAT 完成

- [x] **Task 1.5b** SimpleTex Turbo 端点接入（紧急补丁）✅ 2026-04-25
  - **背景**：调研发现免费云端公式 OCR 仅 SimpleTex 一家可用，标准模型 500/天对 Light 档高频
    防抖触发不够。**SimpleTex turbo 端点免费 2000/天**，必须接入才能让用户敢用实时预览。
  - 改动：
    - `domain/SimpleTexApiRecognizer.kt`：构造器加 `endpoint: SimpleTexEndpoint = Standard` 参数
    - 新增 `SimpleTexEndpoint` 枚举（Standard / Turbo），每个端点带 path + displayName + freeQuota
    - Retrofit service 改用 `@Url` 动态路径，单方法支持两个端点
    - `domain/RecognizerType.kt`：拆 `A2_SimpleTex` → `A2_SimpleTex_Standard` + `A2_SimpleTex_Turbo`
      每个 type 带 displayName + description（下拉显示更直观）
    - `domain/RecognizerRegistry.kt`：两个 SimpleTex 类型共享同一 token 的可用性判断
    - `data/RecognizerPreference.kt`：旧 `A2_SimpleTex` 字符串迁移到 `A2_SimpleTex_Standard`（兼容）
    - `ui/screen/SettingsScreen.kt`：
      - SimpleTex 卡片置顶（推荐路径优先展示）
      - Mathpix 卡片副标题加 `⚠️ 付费` 警示
      - 档位绑定下拉显示 `displayName + description`，让用户一眼看清差异
  - 用户体验：
    - 配置一次 SimpleTex token，下拉同时出现 "SimpleTex Turbo" 和 "SimpleTex 标准" 两个选项
    - 推荐绑定：Light=Turbo（2000/天预览不心疼）/ Deep=Standard（500/天关键识别更准）
    - 每天约 200-400 道公式不会超额
  - **Done 标准**：BUILD SUCCESSFUL；**104/104 单元测试通过**（含 Registry 19 条更新 + Endpoint 5 条新增）

- [x] **Task 1.6** 识别器注册表 + 用户偏好 ✅ 2026-04-25
  - **架构调整**：原 spec 使用 `androidx.security:security-crypto` (EncryptedSharedPreferences)，
    但该库已于 2025-04 在 1.1.0-alpha07 被官方废弃。改用 Google 推荐的现代方案：
    - `androidx.datastore:datastore-preferences:1.1.1`（替代 SharedPreferences）
    - `com.google.crypto.tink:tink-android:1.13.0`（AES256-GCM + Android Keystore 主密钥）
  - 新建 `domain/RecognizerType.kt`：枚举 `{ A1_Mathpix, A2_SimpleTex }` + displayName 属性
  - 新建 `domain/RecognizerSettings.kt`：不可变快照 data class，全字段默认 null/空
  - 新建 `domain/RecognizerRegistry.kt`（**纯函数 object**）：
    - `isAvailable(type, settings)`：A1 需 appId+appKey 双非空；A2 需 token 非空
    - `availableTypes(settings)`：返回当前可用列表，供 SettingsScreen 下拉过滤
    - `instantiate(type, settings)`：按 type + settings 构造对应 `MathOcrRecognizer`，不可用返回 null
    - `resolveLight(settings)` / `resolveDeep(settings)`：按用户绑定解析最终实例（绑定的识别器变为不可用时返回 null）
  - 新建 `data/EncryptedKeyStore.kt`（Tink 包装层）：
    - 主密钥存于 Android Keystore（URI: `android-keystore://formula_master_master_key`）
    - 数据密钥（AES256-GCM keyset）由主密钥加密后存于 SharedPreferences
    - `encrypt(plain): Base64` / `decrypt(cipher): plain`，解密失败安全降级返回空串 + Logcat 警告
  - 新建 `data/RecognizerPreference.kt`（DataStore-backed，响应式 Flow）：
    - 敏感字段（mathpixAppId / mathpixAppKey / simpleTexToken）经 Tink 加密后存 DataStore，明文绝不落盘
    - 非敏感字段（lightRecognizerId / deepRecognizerId 枚举名）明文存储
    - `settings: Flow<RecognizerSettings>` 响应式监听，DataStore 写入后立即 emit
    - 写方法均为 suspend，原子写入（避免 mathpix appId/appKey 半状态）
    - `setLightRecognizer(null)` / `setDeepRecognizer(null)` 解除绑定
    - `clearAll()` 重置全部配置
    - 历史/未来枚举值兼容：未知 type 名解析为 null（不抛异常）
  - **核心设计**：
    - **A1/A2 是独立存在的**，用户可同时配置多个；Light/Deep 各自从已配置可用的识别器中选一个绑定，
      而非全局"只用一个"。两档可绑定同一识别器，也可分开（例：Light=A2 省钱省额度，Deep=A1 求准）
    - 默认 null 是有意设计：**强制用户首次进设置完成配置**，避免静默无识别器导致的"看似工作其实没识别"
      的隐蔽失败模式。与改进点池 [交互] Onboarding 引导（P1）联动 —— 首启检测到两档都为 null 即弹引导
  - **Done 标准**：BUILD SUCCESSFUL；**23 条 RecognizerRegistry 单元测试通过**
    （isAvailable 8、availableTypes 4、instantiate 4、resolveLight/Deep 6、displayName 1）。
    DataStore + Tink 持久化路径需 Android Context，待 Task 1.7 真机端到端验收：
    - 重启 App 后 Light/Deep 绑定 + Key 配置均保留
    - DDMS 文件浏览查看 `datastore/recognizer_prefs.preferences_pb`，敏感 Key 字段为 Base64 密文

- [x] **Task 1.7** SettingsScreen 设置页 ✅ 2026-04-25（待真机验收）
  - **入口决策（已拍板）**：方案 A —— 第 4 个底栏 Tab「设置」（齿轮图标）
  - 工程：
    - `MainScreen.kt` 新增 `AppRoute.Settings`，加入 `topLevelRoutes`（保留底栏可见）
    - 新建 `ui/screen/SettingsScreen.kt`：M3 + ElevatedCard，纵向滚动，三大区
    - 新建 `ui/viewmodel/SettingsViewModel.kt`：桥接 `RecognizerPreference` Flow → StateFlow，
      暴露 set/clear 方法 + `testConnection(type)` 副作用
  - **识别器配置区**：
    - A1 Mathpix 卡片：AppID + AppKey（密码模式，「显示/隐藏」按钮切换可见）+ 「保存」+ 「测试连接」
    - A2 SimpleTex 卡片：UAT Token（密码模式）+ 「保存」+ 「测试连接」
    - 卡片右上角 AssistChip 状态标识：「已配置」（蓝填充）/「未配置」（灰描边）
    - 「保存」按钮在表单 dirty 时启用，写入后变「已保存」灰态
    - 「测试连接」实时显示状态：测试中（CircularProgressIndicator）/ 连接正常（绿勾）/
      失败（红叉 + 原因文案，4 秒后自动消失）
    - 失败原因细分：「Key 无效或已过期」（401/403）/「网络超时」/「无网络连接」/「服务暂时不可用」（5xx）
  - **识别档位绑定区**：
    - 两个 ExposedDropdownMenu：「实时预览（300ms 防抖）」 + 「精确识别（1.5s 防抖）」
    - 仅列出 `RecognizerRegistry.availableTypes(settings)` 返回的可用识别器 + "未绑定"
    - 无可用识别器时下拉禁用，文案变红色提示「请先在上方配置至少一个识别器」
    - 描述文案差异化：Light 强调"识别速度"，Deep 强调"识别准确"
  - **重置区**：
    - OutlinedButton「重置所有配置」（错误色文字），点击弹 AlertDialog 二次确认，调用 `clearAll()`
  - **TestCanvas 联动**（Sprint 1 Task 1.7 核心）：
    - `TestCanvas` 签名改为接收 `lightRecognizer: MathOcrRecognizer?` + `deepRecognizer: MathOcrRecognizer?`
    - `TestScreen` 实例化 `RecognizerPreference`，flow 解析为 settings → `RecognizerRegistry.resolveLight/Deep`
    - 设置页保存 / 切换绑定 → DataStore 写入 → flow 推送 → TestCanvas Composable 重组，
      LaunchedEffect key 含 recognizer，触发立即重新订阅 → **完全无需重启 App**
    - recognizer 为 null 时该档不触发识别（previewLatex/candidates 保持空列表）
  - **Done 标准**：
    - ✅ BUILD SUCCESSFUL on Gradle 9.4.1 / AGP 9.2.0 / KSP 2.3.2
    - ✅ 既有 96/96 单元测试仍全部通过（无回归）
    - ✅ 真机端到端验收（用户填入真实 SimpleTex Key 后通过）：
      - 重启 App 后绑定 + Key 配置保留（DataStore 持久化生效）
      - TestCanvas 写公式 Light / Deep 两档分别用绑定的识别器返回候选
      - 切换绑定无需重启即生效（DataStore Flow → recompose 链路验证）
  - **真机阶段额外修复（用户反馈驱动）**：
    - **测试连接假阳性**：`testConnection()` 之前调 `recognize()` 而该方法吞所有异常
      → 假错误 token 也显示"连接正常"。新增 `MathOcrRecognizer.testConnection()` 接口方法
      不吞异常，A1/A2 各自实现真实鉴权检查
    - **测试按钮卡死 "2s 后可重试"**：状态自动消失（4s）和冷却时长（5s）不一致，外层 tick 提前死掉。
      把 `delay(4000L)` 对齐到 `TEST_COOLDOWN_MS = 5000L`
    - **测试速率限制**：客户端 5s 冷却 + 倒计时 + ViewModel 兜底（即使 UI 失效也拦截重复请求）
    - **强识别交互重构**：移除候选 ModalBottomSheet 弹窗（只有 1 条候选时多此一举），
      Deep 结果直接覆盖 Light 的预览，失败时 PreviewBar 显示 2.5s 红字提示
    - **公式渲染管线重构**（math_template.html + WebViewPool）：
      - **WebView wide-viewport bug**：`useWideViewPort = true`（默认）使 viewport 用屏幕设备宽度而非 WebView frame，
        公式被二次缩放显得很小。改为 `useWideViewPort = false` + `loadWithOverviewMode = false`
      - **`body { height: 100% }` 在 Compose 内嵌 WebView 中失效**：诊断数据显示 `body.clientHeight=8`（仅 padding），
        改用 `100vh + 100vw` 直接绑 viewport
      - **字号自适应**：放弃基于 body 测量的 JS 算法（body 数据不可信），
        改用 CSS `clamp(20px, 55vh, 52px)` 直接由 WebView 渲染层处理
      - **JS 仅做 overflow 兜底**：用 `window.innerWidth/Height`（reliable）测量，超出时缩字号
      - **KaTeX `.katex-display` 默认 1em margin 强制清零**（避免视觉裁切上下半）
      - **左对齐书写习惯**：`place-items: center start` + `.katex-display { text-align: left }`
    - **诊断驱动开发**：当反复猜测失败时，在 HTML 模板插入 diag 角标显示 body/win/dpr/font 等运行时数据，
      用户一张截图就能定位，避免靠 Logcat 反复来回

- [x] **Task 1.8** 识别器不可用时的友好降级 ✅ 2026-04-29
  - 场景覆盖：
    - **两档都未绑定**：用户写下第一笔时触发一次 Snackbar"尚未绑定识别器，写下来也不会识别"
      + 「去设置」action（不重复弹，直到清空画布）
    - **绑定的识别器变为不可用**（用户清空了 Key）：因 `RecognizerRegistry.resolveLight/Deep`
      返回 null，等同未绑定处理；同一 Snackbar 路径
    - **Deep 识别请求失败**（Key 无效 / 网络超时 / 无网络 / 5xx 等）：使用
      [`RecognizerErrorClassifier`] 分类的简短文案，Snackbar 显示"强识别失败：&lt;原因&gt;"
      Key 类错误（401/403）额外附带「去设置」action
    - **Light 识别失败**：完全静默（仅 Logcat），符合 Q3 决策——自动防抖每次失败 Snackbar 太烦扰
  - 实现要点：
    - 新建 `domain/RecognizerErrorClassifier.kt`（pure object），与 `SettingsViewModel.testConnection`
      共用同一份分类逻辑，避免两处分别维护
    - `TestCanvas` 移除内部 `deepFailureMessage` 红字状态，错误信号通过 `onDeepFailure: (Throwable) -> Unit`
      callback 上抛宿主，遵循 UDF
    - `TestCanvas` 加 `onWritingButNoRecognizer: () -> Unit`，由内部 `unboundNotifiedThisSession`
      去重避免重复 Snackbar
    - `MainScreen.kt` 在 `composable(AppRoute.Test.route)` 中传入 `onNavigateToSettings` 回调，
      切到 Settings Tab（保留底部导航 + 状态恢复）
  - **Done 标准**：
    - ✅ BUILD SUCCESSFUL on Gradle 9.4.1 / AGP 9.2.0 / KSP 2.3.2
    - ✅ 既有单元测试全部通过（无回归）
    - 真机端到端验收（待用户操作）：
      - 不绑定任何识别器，进入严测画布写字 → "尚未绑定识别器" Snackbar 弹出
      - 点「去设置」→ 直接切到设置 Tab
      - 设错 token 后画字 + 点强识别 → "强识别失败：Key 无效或已过期" Snackbar
      - 飞行模式下点强识别 → "强识别失败：无网络连接" Snackbar

- [x] **Task 1.9** 识别失败反馈机制 ✅ 2026-04-29
  - 数据层：
    - 新建 `data/local/entity/OcrFeedbackEntity`（id / createdAt / formulaId? / recognizerType /
      mode / strokesJson / candidatesJson / correctLatex）
    - 新建 `data/local/dao/OcrFeedbackDao`（insert / getAll / countFlow / clearAll）
    - `AppDatabase` 升 v1 → v2，加 `ocr_feedback` 表；用 `fallbackToDestructiveMigration(dropAllTables=true)`
      （打磨阶段允许重置，formulas.json 自动重新预加载）
  - UI 层：
    - `TestCanvas` PreviewBar 加「都不对」TextButton（material-icons-core 不含 ThumbDown，
      用文字标签替代），点击通过 `onReportFeedback(FeedbackPayload)` 上抛
    - 新建 `ui/component/FeedbackDialog`：等比缩放笔画预览 + 候选 chip 列表 + 手输正确 LaTeX 输入框
    - `TestScreen` 持有 `pendingFeedback: FeedbackPayload?` state，弹 Dialog + 入库后 Snackbar 提示
    - `TestViewModel.submitOcrFeedback()`：Gson 序列化 strokes/candidates 后落库
  - 导出能力：
    - 设置页底部加「识别反馈」区：显示已收集样本数（响应式，DAO countFlow 驱动）+
      「导出 JSON」+「清空」按钮（清空带二次确认）
    - 「导出 JSON」走 SAF（`ActivityResultContracts.CreateDocument("application/json")`），
      文件名带时间戳前缀 `formulamaster_ocr_feedback_yyyyMMdd_HHmmss.json`，避开存储权限
    - 导出结果通过 `ExportResult` sealed class（NoSamples / Success / Failed）反馈到 SettingsScreen Snackbar
  - **Done 标准**：
    - ✅ BUILD SUCCESSFUL；既有单元测试全部通过
    - 真机端到端验收（待用户操作）：
      - 严测页写字识别后点「都不对」→ Dialog 显示笔画 + 候选 + 输入框
      - 输入正确 LaTeX 提交 → "已记录反馈，可在设置页导出 JSON" Snackbar
      - 设置页"识别反馈"区数字 +1
      - 点「导出 JSON」→ SAF 选位置保存 → 打开文件可读、字段完整
      - 点「清空」+ 二次确认 → 数字归零

### 验收标准（全部 Task 完成后）

- `./gradlew.bat compileDebugKotlin` BUILD SUCCESSFUL
- 真机上 A1（Mathpix）和 A2（SimpleTex）分别填入有效 Key 后均可识别公式
- 用户可在设置页独立绑定 Light / Deep 两档使用的识别器，切换即时生效（无需重启）
- API Key 加密存储，明文不可见（DDMS 文件浏览验证）
- 任一识别器未配置 / 连接失败时，TestCanvas 不崩溃，Snackbar 文案准确
- 改进点池中被本 Sprint 消费的条目已移动到"已纳入"分区

---

## Sprint 2：性能修复 + 时间设置体系（2026-04-29 ~ 进行中）

### 背景

Sprint 1 收尾后用户反馈了一组改进点（见 [`../改进点池.md`](../改进点池.md)）。本 Sprint 取
TOP 5 条目落地：
- **P0 卡顿/残留**：独立度高，用诊断驱动定位 → 修复，奠定后续 UI 打磨阶段的体验底盘
- **P0 复习时间刷新点**：解决"上下半场复习割裂"问题，对齐用户心智模型
- **P1 冲刺日期自设置 + Onboarding**：与刷新时间设置共用 DataStore + 设置页 boilerplate

不纳入本 Sprint 的 P0 "学习/复习流程重构"——范围未定，按方法论 #3 待用户细化后再开。

### 关键决策记录

- **默认刷新时刻**：08:00（用户拍板，2026-04-29）
- **默认冲刺目标日期**：动态计算为"当前年份 12 月 20 日"（考研日期常用值）
- **时区**：读取 `ZoneId.systemDefault()`，UI 仅展示不允许修改
- **学习/复习流程重构**：本 Sprint 不起 RFC，等用户细化痛点后单独立项

### Task 列表

- [x] **Task 2.1** 切换界面卡顿 + 残留诊断与修复 ✅ 2026-04-30
  - **Phase A · 诊断**（按方法论 #2，1 轮拿到结论）：
    - `WebViewPool` 加 acquire/release/warmUp 时序日志（DIAG_TAG = `PerfDiag.Pool`）
    - `MathFormulaView` 加 ENTER/FACTORY/UPDATE/READY/LEAVE/LOAD 事件日志（`PerfDiag.MathView`）
    - `MainScreen` 加路由变化时间戳日志（`PerfDiag.Nav`）
    - 用户真机（Nothing A065 / Android 16）复现 + 提供两份完整 logcat（22:16:43 / 08:37:22）
    - 三类根因定位：WebView release 不清空旧内容 / NavHost 全无 transition / 详情页同时挂 2 个 WebView 但池只预热 1 个
  - **Phase B · 修复**（共 8 处）：
    - **A** `WebViewPool.release` 增加 `webView.loadUrl("about:blank")`，避免复用时旧 KaTeX 残留
    - **B** NavHost 全部 transition 改为 `fadeIn/fadeOut(tween(120))`（详情页 slide 不受影响）
    - **C** `MathFormulaView` 重构：`LaunchedEffect(latex, isDark)` 接管加载；`WebViewClient.onPageFinished` 触发 `contentReady=true`；上层 `Box` 用 `AnimatedVisibility` 渲染 surface 同色遮罩，加载完淡出 200ms
    - **D** 新建 `data/AppContainer.kt`：进程级 service locator，托管 `RecognizerPreference` + `AppDatabase` 单例，避免每次 ViewModel 创建都重建 EncryptedKeyStore
    - **E** TestScreen `onExit` 改为 `navController.navigateUp()`（兜底回 Memory），让 Memory→详情→某入口进入 Test 时退出能回详情页
    - **F** "Test 移出 NavBar"作为改进点加入改进点池（UX 重构，本 Sprint 不做）
    - **G** `MainActivity.onCreate` 加冷启动后台预热（Dispatchers.IO）：`pref.settings.first()` 触发 DataStore 读盘 + Tink AEAD lazy 初始化；`WebViewPool.warmUp(count = 2)` 提升到 2 个（详情页同时挂 2 个）
    - **H** `RecognizerPreference.settings` 升级为 process 级 hot StateFlow（`stateIn(applicationScope, Eagerly, ...)`），`SettingsViewModel` 去掉自己的 `stateIn`；消除"每次进 SettingsScreen 都要重做 DataStore 读盘 + Tink 解密"的根因
  - **遗留**：
    - 冷启动后首次进 Settings 仍可感知到"加载一小下"。已加入改进点池（[性能] 进一步优化
      SettingsScreen 进入瞬间的"加载感"，P2）。可能根因为 SettingsScreen 自身 Compose 树深
      + ExposedDropdownMenuBox / SAF launcher 注册等，需 Profiler 进一步定位
  - **诊断代码处理**：所有 `DIAG_ENABLED` 开关复位为 `false`，**保留代码备查**，将来再排性能问题打开即可。搜索 `[PerfDiag]` 可一键定位
  - **Done 标准**：
    - ✅ BUILD SUCCESSFUL；既有单元测试无回归
    - ✅ 真机连续 Tab 切换 + push/pop 详情页无可见残留（用户确认"已经好了很多"）
    - ✅ 详情页 / 复习卡片切换无 KaTeX 残留
    - ✅ Test 切换流畅度大幅改善（用户确认"目前可使用"）
    - ⏳ Settings 加载感残留 → 已转为改进点池条目，下个 Sprint 视情况评估
  - **关联文件**：
    - 新建：`data/AppContainer.kt`、`domain/RecognizerErrorClassifier.kt`（属 Sprint 1 Task 1.8 但本 Task 引用）
    - 重写：`ui/component/MathFormulaView.kt`、`ui/component/WebViewPool.kt`
    - 修改：`ui/screen/MainScreen.kt`、`ui/screen/TestScreen.kt`、`ui/viewmodel/SettingsViewModel.kt`、`ui/viewmodel/TestViewModel.kt`、`data/RecognizerPreference.kt`、`MainActivity.kt`

- [x] **Task 2.2** ReviewScheduler 复习时间截断到当日刷新时刻 ✅ 2026-04-30
  - 算法层重构：
    - `domain/ReviewScheduler.kt` 的 `calculate()` 返回的 `nextReviewTime` 截断到
      "目标日 hourOfDay:00（本地时区）"，而非毫秒精度
    - 新增内部工具函数 `truncateToRefreshHour(timeMs, hourOfDay, zoneId)` (`internal`)
    - `calculate()` 新增 `hourOfDay: Int = 8` 和 `zoneId: ZoneId = ZoneId.systemDefault()` 参数，
      既有调用（TestViewModel / ReviewViewModel）无需修改（默认值兼容）
    - 截断逻辑：rawNextReviewTime → 截断到同日刷新整点；若刷新整点已过（极短稳定性场景），
      用 `ZonedDateTime.plusDays(1)` DST 安全顺延到次日同一整点
  - 数据查询层：`StudyStateDao.getDueFormulas` 判断不变（nextReviewTime ≤ now），
    效果是"过当日 hourOfDay:00 才进入可复习"，消除同日多次复习的时间割裂
  - **Done 标准**：
    - ✅ BUILD SUCCESSFUL
    - ✅ 单元测试：原 6 条不回归 + 新增 7 条（共 13/13），覆盖：
      - 同日 09:00 / 15:00 复习 → nextReviewTime 相同
      - `truncateToRefreshHour` 在 UTC / Asia/Shanghai / America/Los_Angeles 三时区截断正确
      - 美国 2024-03-10 DST 春令时切换日截断不抖动
      - 极短稳定性（S=0.2）+ 刷新整点已过 → 顺延到次日 08:00
      - `hourOfDay=20` 端到端截断落点正确

- [x] **Task 2.3** 时区 + 复习刷新时间用户设置 UI ✅ 2026-04-30
  - 新建 `data/AppPreference.kt`（DataStore Preferences-backed）：
    - `dailyRefreshHourOfDay: Int`（默认 8）
    - `targetExamDate: Long`（默认值由 Task 2.4 落实，本 Task 占位）
    - 时区不存储，运行期 `ZoneId.systemDefault()` 直读
    - 响应式 `Flow<AppSettings>` 暴露
  - 设置页加新区"学习计划"（位于"识别档位绑定"和"识别反馈"之间）：
    - **复习刷新时间**：TimePicker（24h 制），描述文案"每天 X 点起开始计算今日复习"
    - 时区只读展示：`Text("当前时区：${ZoneId.systemDefault()}")`
  - `ReviewScheduler` 调用方（ReviewViewModel / TestViewModel）从 `AppPreference.dailyRefreshHourOfDay`
    取值传入 calculate
  - **Done 标准**：
    - BUILD SUCCESSFUL
    - 设置页能改刷新时间，重启 App 后保留（DataStore 验证）
    - 改后立即生效（DataStore Flow → ViewModel 重新订阅 → 下一次 calculate 用新值）

- [x] **Task 2.4** 冲刺目标日期用户自设置 ✅ 2026-05-01
  - `data/AppPreference.kt` 加 `targetExamDate: Long`：
    - 默认值动态计算："当前年份 12 月 20 日 00:00（本地时区）"
    - 用户已设过 → 持久化值；未设过 → 默认值
  - `data/AppConfig.kt` 中硬编码 `targetExamDate` 标 deprecated，改读 AppPreference
  - `domain/SprintModeManager.kt`：
    - `isActive(targetExamDate: Long)` 接受参数（不再读全局常量）
    - 调用方从 ViewModel 注入 AppPreference 流
  - 设置页"学习计划"区加：
    - **考试目标日期**：DatePicker（M3 `DatePickerDialog`）
    - 显示当前距考试天数（"距考试还有 N 天"）
    - "重置为默认（${当前年}-12-20）"按钮
  - **首次启动**：未填则用默认值（不强制弹窗，由 Task 2.5 Onboarding 接管引导）
  - **Done 标准**：
    - BUILD SUCCESSFUL
    - 改日期立即影响 SprintModeManager 判定
    - DatePicker 不允许选过去日期（disable 逻辑）

- [x] **Task 2.5** 首次启动 Onboarding 引导 ✅ 2026-05-01
  - 触发条件：`AppPreference.firstLaunchCompletedAt == 0L`
    （DataStore 加 `firstLaunchCompletedAt: Long` 字段，完成引导后写当前时间戳）
  - 引导流程（M3 BottomSheet 多步，or 全屏 4 页 HorizontalPager，待实施时定）：
    1. **欢迎页**：项目简介 + "为考研学子打造的公式记忆 App"
    2. **设置目标日期**：DatePicker，默认当前年份 12-20，可改可跳过
    3. **配置识别器**：链接到设置页 Mathpix / SimpleTex 区（可跳过，标注"也可稍后在设置页配置"）
    4. **复习时间偏好**：TimePicker 默认 08:00，可改可跳过
    5. **完成**：写入 `firstLaunchCompletedAt`，跳到 Memory Tab
  - 任意步骤跳过都视为引导完成（不再触发）
  - 提供"跳过引导"全局按钮（每页右上角）
  - **设置页加"重置 Onboarding"** 入口（藏在重置区，便于调试）
  - **Done 标准**：
    - 全新安装 / clearData 后启动看到引导
    - 完成或跳过后不再触发
    - 重启 App 不重复弹（持久化生效）

### 验收标准（全部 Task 完成后）

- `./gradlew.bat compileDebugKotlin` BUILD SUCCESSFUL
- 单元测试无回归 + 至少新增 6 条 ReviewScheduler 时间截断 case
- 真机端到端：
  - Tab 切换流畅无残留（Task 2.1）
  - 上午 + 下午分别复习的内容次日 08:00 同时进入可复习（Task 2.2/2.3）
  - 设置页能改刷新时间 + 考试日期，重启后保留（Task 2.3/2.4）
  - 全新安装看到 Onboarding，完成或跳过后不再触发（Task 2.5）
- 改进点池中被本 Sprint 消费的条目已移动到"已纳入"分区

---

## Sprint 3：输入方式 + 反馈机制重构（2026-05-01 启动）

### 背景

Sprint 2 收尾后扫描改进点池，按 (优先级 ASC, 时间 ASC) 取 P1 已拍板条目落地：

- **输入方式三选一 → 二选一**（用户 2026-05-01 拍板）：LaTeX 输入法因自研工程量过大、
  第三方组件不可用而**无限期搁置**（已移入"已拒绝/搁置"分区）。仅保留"手写识别 / 纸笔自评"
- **OcrFeedback 数据模型重构**（用户 2026-05-01 拍板，新增）：原"手输正确 LaTeX"设计
  随 LaTeX 输入法搁置失效，改为"公式 token 选错位"——读取当前公式 LaTeX，拆 token
  让用户多选哪些部件被识别错
- **Test 移出 NavBar**（待 3 个澄清问题确认后落地）

### 关键决策记录

- **LaTeX 输入法**：无限期搁置，等"质量过硬的开源 Compose LaTeX 输入面板"或"纸笔自评
  局限暴露明显"才重启
- **OcrFeedback schema 升级**：加 `formulaLatex`（自动读取）和 `wrongTokensJson`（用户多选），
  `correctLatex` 改 nullable 兼容旧数据；`AppDatabase` v2 → v3，沿用
  `fallbackToDestructiveMigration(dropAllTables=true)`（打磨阶段允许）
- **Sprint 3 收尾后**：进入 P0「学习与复习流程重构」task（用户拍板路线 A 后接续）

### Task 列表

- [ ] **Task 3.1** InputMode 基础设施
  - 新建 `domain/InputMode` 枚举：`Handwriting`（默认）/ `PaperPen`
  - `AppPreference.AppSettings` 加 `inputMode: InputMode = Handwriting`
  - DataStore key + setter `setInputMode(mode)`
  - `SettingsViewModel` 暴露 + setter；`SettingsScreen` 加"输入偏好"区
    （ExposedDropdown 二选一 + 描述文案）
  - **Done 标准**：BUILD SUCCESSFUL；切换后重启保留；现有调用方未读 `inputMode` 不受影响

- [ ] **Task 3.2** 纸笔自评模式 PaperPenInputArea
  - 新建 `ui/component/PaperPenInputArea.kt`：
    - 大按钮"已完成默写"
    - 点击后展开标准答案（MathFormulaView 渲染）+ 自评按钮「完全正确」/「出现错误」
  - `TestScreen` 按 `appSettings.inputMode` 路由：
    - `Handwriting` → 现有 TestCanvas 路径
    - `PaperPen` → `PaperPenInputArea` 路径（不调识别器，不消耗额度）
  - 自评结果调 `viewModel.submitJudgment(item, isCorrect, costTimeMs)`，与现有路径对齐
  - **Done 标准**：BUILD SUCCESSFUL；真机切到纸笔模式不再调用识别器（Logcat 验证）；
    自评后正确进入下一题

- [ ] **Task 3.3** Onboarding 加"输入方式"页
  - 在欢迎页和考试日期页之间插入新页（位序 1，原 1-4 后移到 2-5；总 6 页）
  - 描述文案：手写识别（需识别器，体验流畅）/ 纸笔自评（不依赖识别，需自己判断）
  - 默认勾选"手写识别"
  - 改 `OnboardingViewModel.completeAndPersist` 增加 `inputMode` 参数
  - **Done 标准**：BUILD SUCCESSFUL；引导能选输入方式 + 持久化；选纸笔后跳过识别器配置页

- [ ] **Task 3.4** OcrFeedback 数据模型重构 🔥
  - 数据层：
    - `OcrFeedbackEntity` 加 `formulaLatex: String?` + `wrongTokensJson: String?` 字段；
      `correctLatex` 改 nullable
    - `AppDatabase` v2 → v3，`fallbackToDestructiveMigration(dropAllTables=true)`
  - LaTeX token 化：
    - 调研 `ClozeParser` 是否可复用；不行则新建 `domain/LatexTokenizer.kt`
    - 切分粒度：`\command` / 上下标块 `^{...}` `_{...}` / 大括号块 / 单字符
    - 单元测试覆盖 6 类典型公式（短表达式 / 上下标 / 分式 / 求和 / 积分 / 嵌套）
  - UI 层：
    - `FeedbackDialog` 替换"输入正确 LaTeX"为：
      - 上半部：笔画预览 + 候选 chip 列表（保留）
      - 中部：当前公式渲染 (MathFormulaView)
      - 下半部：FlowRow + FilterChip 显示 token 列表，多选高亮
      - "标记错误部件"按钮（多选确认后提交）+「都不对」快捷（自动选所有 token）
    - `TestViewModel.submitOcrFeedback` 接收 `wrongTokens: List<String>` 替代 `correctLatex`
  - 导出 JSON 格式同步更新（schema 演进）
  - **Done 标准**：
    - BUILD SUCCESSFUL；新增 LatexTokenizer 单测全绿
    - 真机：进入反馈 → 选 token chip → 提交 → 设置页样本数 +1 → 导出 JSON 含新字段

- [ ] **Task 3.5** Test 移出 NavBar
  - 先确认 3 个待澄清问题（已知 spec 列出）：
    1. 严测入口图标用什么（Edit / Quiz / Speed / 其他）？
    2. 入口位置：MemoryScreen TopAppBar action 还是右下 FAB？
    3. 数据未掌握时入口是否禁用 + 提示？
  - 实施：
    - `MainScreen.tabs` 去掉 Test；`topLevelRoutes` 同步移除
    - `MemoryScreen` 加严测入口（位置 + 图标按用户决定）
    - 路由从 NavBar item onClick 改为 IconButton onClick
    - 通知 navTarget 处理（如有指向 Test 的场景重新审视）
  - **Done 标准**：BUILD SUCCESSFUL；从 Settings/Review Tab 进入 Test 后退出能回原 Tab

### 验收标准（全部 Task 完成后）

- `./gradlew.bat compileDebugKotlin testDebugUnitTest` BUILD SUCCESSFUL
- 真机端到端：
  - 设置页可切输入方式，重启保留
  - 切到纸笔自评后 TestScreen 不调识别器
  - Onboarding 包含输入方式选择
  - 反馈 Dialog 用 token 多选替代 LaTeX 输入框
  - Test 从任一来源 Tab 进入退出回原页
- 改进点池中被本 Sprint 消费的条目移动到"已完成"分区

---

## Sprint 完成记录

### Sprint 1 总结（2026-04-24 ~ 2026-04-29）

完成手写识别真实落地：A1 Mathpix + A2 SimpleTex（Standard + Turbo）双 API 路线；
ML Kit Digital Ink 因不支持数学公式而拒绝；新建识别器注册表 + 用户偏好（DataStore + Tink 加密）；
SettingsScreen 三大区块（识别器配置 / 档位绑定 / 反馈管理）；TestCanvas 接入双档识别 +
友好降级 Snackbar；OcrFeedback 收集机制（落库 + SAF 导出）。LaTeX 规范化后处理器 +
公式渲染管线重构（KaTeX HTML 模板 + WebViewPool）。

### Sprint 2 总结（2026-04-29 ~ 2026-05-01）

完成性能修复 + 时间设置体系：诊断驱动定位 7 处切换卡顿根因并修复；引入 `AppPreference`
（DataStore + isLoaded 信号）持久化 `dailyRefreshHour/Minute`、`targetExamDate`、
`firstLaunchCompletedAt`；`ReviewScheduler` 新增 `truncateToRefreshHour` /
`adjustToRefreshHour`（DST 安全）+ 13/13 单测覆盖三时区 + DST 边界；
`SprintModeManager` 改参数注入；设置页加"学习计划"区（TimePicker / DatePicker）；
默认考试日期改"12 月倒数第二个周六"动态计算；OnboardingScreen 5 页全屏 HorizontalPager
完整引导。修复多处时间逻辑漏洞（首次激活 / 切换刷新时刻批量重写库存 / 通知时间同步）。
