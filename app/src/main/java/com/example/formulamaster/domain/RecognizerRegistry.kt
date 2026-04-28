package com.example.formulamaster.domain

/**
 * 识别器注册表（纯函数业务逻辑）。
 *
 * 职责：
 * - **可用性探测**：根据 [RecognizerSettings] 中的 Key 配置判断识别器是否可用
 * - **实例化**：按 type + settings 构造对应 [MathOcrRecognizer] 实例
 * - **可用列表**：供 SettingsScreen 下拉菜单过滤
 *
 * 设计原则：
 * - **纯函数**：所有方法仅依赖入参，无副作用，便于 JVM 单元测试
 * - **添加新识别器仅改此处**：UI / 调用方零修改（以后接入 L2 端侧时只动这个文件 +
 *   [RecognizerType] 枚举 + [RecognizerSettings] 字段）
 */
object RecognizerRegistry {

    /**
     * 判断识别器是否可用（=配置完整，可正常发起识别请求）。
     *
     * 注意：本方法**不**校验 Key 在服务端是否有效，仅校验本地配置完整性。
     * 服务端 Key 是否有效需 SettingsScreen 的「测试连接」按钮验证。
     */
    fun isAvailable(type: RecognizerType, settings: RecognizerSettings): Boolean = when (type) {
        RecognizerType.A1_Mathpix ->
            settings.mathpixAppId.isNotBlank() && settings.mathpixAppKey.isNotBlank()
        RecognizerType.A2_SimpleTex_Standard,
        RecognizerType.A2_SimpleTex_Turbo ->
            settings.simpleTexToken.isNotBlank()
    }

    /**
     * 返回当前所有可用的识别器类型（按枚举声明顺序）。
     * 供 SettingsScreen 「Light/Deep 档绑定」下拉菜单过滤使用。
     */
    fun availableTypes(settings: RecognizerSettings): List<RecognizerType> =
        RecognizerType.entries.filter { isAvailable(it, settings) }

    /**
     * 按 type + settings 实例化识别器。
     *
     * @return 配置完整时返回对应识别器实例；不可用时返回 null（调用方据此降级）。
     */
    fun instantiate(type: RecognizerType, settings: RecognizerSettings): MathOcrRecognizer? {
        if (!isAvailable(type, settings)) return null
        return when (type) {
            RecognizerType.A1_Mathpix ->
                MathpixApiRecognizer(settings.mathpixAppId, settings.mathpixAppKey)
            RecognizerType.A2_SimpleTex_Standard ->
                SimpleTexApiRecognizer(settings.simpleTexToken, SimpleTexEndpoint.Standard)
            RecognizerType.A2_SimpleTex_Turbo ->
                SimpleTexApiRecognizer(settings.simpleTexToken, SimpleTexEndpoint.Turbo)
        }
    }

    /**
     * 解析 Light 档绑定的识别器（结合 [settings.lightRecognizerId] 与可用性）。
     *
     * @return 已绑定且可用 → 对应实例；未绑定或绑定的识别器变为不可用 → null
     */
    fun resolveLight(settings: RecognizerSettings): MathOcrRecognizer? =
        settings.lightRecognizerId?.let { instantiate(it, settings) }

    /**
     * 解析 Deep 档绑定的识别器。同 [resolveLight] 但用 [settings.deepRecognizerId]。
     */
    fun resolveDeep(settings: RecognizerSettings): MathOcrRecognizer? =
        settings.deepRecognizerId?.let { instantiate(it, settings) }
}
