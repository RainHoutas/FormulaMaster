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

  > 备选：`/api/latex_ocr_turbo`（轻量模型，速度优先）—— 暂不接入，作为改进点池条目

- [ ] **Task 1.6** 识别器注册表 + 用户偏好
  - 新建 `domain/RecognizerRegistry.kt`：
    - 枚举 `RecognizerType { A1_Mathpix, A2_SimpleTex }`（L1 已拒绝，未来 L2 端侧 TFLite 上线后扩枚举）
    - `isAvailable(type, prefs): Boolean`：A1 需要 appId+appKey 都非空；A2 需要 token 非空
    - `instantiate(type, prefs): MathOcrRecognizer?`：按 type 创建对应 Recognizer 实例（注入 Key），不可用则返回 null
  - 新建 `data/RecognizerPreference.kt`，存储以下字段（EncryptedSharedPreferences）：
    - `lightRecognizerId: RecognizerType?`（Light 档用哪个，**默认 null = 未绑定**）
    - `deepRecognizerId: RecognizerType?`（Deep 档用哪个，**默认 null = 未绑定**）
    - `mathpixAppId: String`、`mathpixAppKey: String`（A1 所需）
    - `simpleTexToken: String`（A2 所需）
  - **核心设计**：
    - **A1/A2 是独立存在的**，用户可同时配置多个；Light/Deep 各自从已配置可用的识别器中选一个绑定，
      而非全局"只用一个"。两档可绑定同一识别器，也可分开（例：Light=A2 省钱省额度，Deep=A1 求准）
    - 默认 null 是有意设计：**强制用户首次进设置完成配置**，避免静默无识别器导致的"看似工作其实没识别"
      的隐蔽失败模式。与改进点池 [交互] Onboarding 引导（P1）联动 —— 首启检测到两档都为 null 即弹引导
  - 依赖：`androidx.security:security-crypto`
  - **Done 标准**：重启 App 后 lightRecognizerId / deepRecognizerId / Keys 均持久化；
    Key 明文不可见（DDMS 文件浏览验证）；`isAvailable()` 对未配置 Key 的识别器返回 false

- [ ] **Task 1.7** SettingsScreen 设置页
  - 新增第 4 个顶级路由 or 长按底栏入口（**开工前决策，先问用户**）
  - **识别器配置区**（每个识别器独立一块卡片，未配置 Key 的识别器卡片显示"未配置"角标）：
    - **A1 Mathpix**：AppID + AppKey 两个输入框（密码模式 / 显示切换图标）+ 「测试连接」按钮
      - 测试连接：发送最小尺寸 dummy 图片到 v3/text，按钮旁实时反馈（✅ 连接正常 / ❌ Key 无效 / ❌ 网络超时）
    - **A2 SimpleTex**：Token 输入框（密码模式）+ 「测试连接」按钮（同 A1）
    - （未来 L2 端侧 TFLite 上线后，本区会自动追加新卡片，无需 UI 重构）
  - **识别档位绑定区**：
    - 「实时预览（300ms 防抖）使用」 → 下拉菜单，**仅列出当前 `isAvailable()=true` 的识别器** + "未绑定"
    - 「精确识别（1.5s 防抖）使用」 → 下拉菜单，同上
    - 两档可绑定同一识别器，也可分别绑定不同识别器
    - 选择"未绑定"等同于关闭该档识别（写字时该档不触发请求）
  - **降级提示**：当某档绑定的识别器变为不可用（用户清空了 Key 等），下拉显示警告状态 + 引导重新绑定
  - **Done 标准**：
    - 设置保存后 TestCanvas 的 Light / Deep 两档分别使用用户绑定的识别器，无需重启 App
    - 切换绑定即时生效（StateFlow 推送）
    - 「测试连接」对有效/无效 Key 区分明确，文案准确
    - 真机填入有效 Mathpix Key + 写一个公式 → Deep 档返回非空候选（端到端验收）

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
