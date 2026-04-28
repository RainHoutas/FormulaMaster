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

## Sprint 1：手写识别真实落地（2026-04-24 ~ 进行中）

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

- [ ] **Task 1.8** 识别器不可用时的友好降级
  - 场景覆盖：
    - **两档都未绑定**：TestCanvas 写字时不触发任何请求，显示 Snackbar"尚未绑定识别器" + 「去设置」按钮
    - **绑定的识别器变为不可用**（用户清空了 Key）：等同未绑定处理
    - **请求失败**（Key 无效 / 网络超时 / 无网络 / 服务异常）：分类 Snackbar 文案
  - 识别异常分类展示（与 Task 1.4 已实现的错误兜底联动）：
    - "Key 无效，请检查设置" → 引导设置页
    - "网络超时，请稍后重试"
    - "无网络连接，请检查网络"
    - "服务异常，请稍后重试"
  - **Done 标准**：所有失败状态下 TestCanvas 不崩溃，Snackbar 文案准确分类，「去设置」入口直达 SettingsScreen

- [ ] **Task 1.9** 识别失败反馈机制
  - TestCanvas 候选展示区旁加一个"都不对？"小按钮
  - 点击后保存当前笔画 + 用户手输正确 LaTeX 到 `data/local/entity/OcrFeedbackEntity`
  - 提供导出为 JSON 的入口（设置页）
  - **Done 标准**：失败样本正常落库，导出 JSON 格式可读；累计到一定量作为将来可能 L2 自训练的数据源

### 验收标准（全部 Task 完成后）

- `./gradlew.bat compileDebugKotlin` BUILD SUCCESSFUL
- 真机上 A1（Mathpix）和 A2（SimpleTex）分别填入有效 Key 后均可识别公式
- 用户可在设置页独立绑定 Light / Deep 两档使用的识别器，切换即时生效（无需重启）
- API Key 加密存储，明文不可见（DDMS 文件浏览验证）
- 任一识别器未配置 / 连接失败时，TestCanvas 不崩溃，Snackbar 文案准确
- 改进点池中被本 Sprint 消费的条目已移动到"已纳入"分区

---

## Sprint 2+（待从改进点池生成）

> Sprint 1 完成后，扫描 [`../改进点池.md`](../改进点池.md) 的"待评估"分区生成。

---

## Sprint 完成记录

（Sprint 完成后在此追加简短总结，方便回溯）
