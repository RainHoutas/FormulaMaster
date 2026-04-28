package com.example.formulamaster.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Sprint 1 Task 1.6 + 1.5b — RecognizerRegistry 单元测试
 *
 * 验证识别器可用性 + 实例化 + Light/Deep 解析的纯函数路径，全部 JVM 单测无需 Android framework。
 *
 * 双 SimpleTex 类型（Standard + Turbo）共享同一 token：
 * - 用户配置一次 token，两个类型同时可用
 * - 用户可独立绑定到 Light / Deep 两档（推荐 Light=Turbo / Deep=Standard）
 *
 * 不验证（依赖 Android Context / Tink / Keystore，需 Robolectric 或 androidTest）：
 * - [com.example.formulamaster.data.RecognizerPreference] 的 DataStore 持久化
 * - [com.example.formulamaster.data.EncryptedKeyStore] 的 Tink 加解密
 */
class RecognizerRegistryTest {

    // ── 1. isAvailable —— 配置完整性判断 ────────────────────────────────

    @Test fun `A1 不可用 当 appId 缺失`() {
        val s = RecognizerSettings(mathpixAppId = "", mathpixAppKey = "key")
        assertFalse(RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, s))
    }

    @Test fun `A1 不可用 当 appKey 缺失`() {
        val s = RecognizerSettings(mathpixAppId = "id", mathpixAppKey = "")
        assertFalse(RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, s))
    }

    @Test fun `A1 不可用 当两个 Key 都缺失`() {
        assertFalse(RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, RecognizerSettings()))
    }

    @Test fun `A1 不可用 当 appId 是纯空白`() {
        val s = RecognizerSettings(mathpixAppId = "   ", mathpixAppKey = "key")
        assertFalse("纯空白应视为未配置",
            RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, s))
    }

    @Test fun `A1 可用 当两个 Key 都填了`() {
        val s = RecognizerSettings(mathpixAppId = "id", mathpixAppKey = "key")
        assertTrue(RecognizerRegistry.isAvailable(RecognizerType.A1_Mathpix, s))
    }

    @Test fun `A2 Standard 不可用 当 token 缺失`() {
        assertFalse(RecognizerRegistry.isAvailable(
            RecognizerType.A2_SimpleTex_Standard, RecognizerSettings()))
    }

    @Test fun `A2 Turbo 不可用 当 token 缺失`() {
        assertFalse(RecognizerRegistry.isAvailable(
            RecognizerType.A2_SimpleTex_Turbo, RecognizerSettings()))
    }

    @Test fun `A2 Standard 不可用 当 token 是纯空白`() {
        val s = RecognizerSettings(simpleTexToken = "  \n\t  ")
        assertFalse(RecognizerRegistry.isAvailable(RecognizerType.A2_SimpleTex_Standard, s))
    }

    @Test fun `A2 Standard 和 Turbo 共享 token —— 配置一次两个都可用`() {
        val s = RecognizerSettings(simpleTexToken = "tok")
        assertTrue("Standard 应可用",
            RecognizerRegistry.isAvailable(RecognizerType.A2_SimpleTex_Standard, s))
        assertTrue("Turbo 应可用",
            RecognizerRegistry.isAvailable(RecognizerType.A2_SimpleTex_Turbo, s))
    }

    // ── 2. availableTypes —— 过滤可用列表 ────────────────────────────────

    @Test fun `availableTypes 空设置返回空列表`() {
        assertTrue(RecognizerRegistry.availableTypes(RecognizerSettings()).isEmpty())
    }

    @Test fun `availableTypes 只配置 A1 时返回 A1`() {
        val s = RecognizerSettings(mathpixAppId = "id", mathpixAppKey = "key")
        assertEquals(listOf(RecognizerType.A1_Mathpix), RecognizerRegistry.availableTypes(s))
    }

    @Test fun `availableTypes 只配置 SimpleTex token 时返回 Standard 和 Turbo 两条`() {
        val s = RecognizerSettings(simpleTexToken = "tok")
        val available = RecognizerRegistry.availableTypes(s)
        assertEquals("应有两条", 2, available.size)
        assertTrue("含 Standard", available.contains(RecognizerType.A2_SimpleTex_Standard))
        assertTrue("含 Turbo", available.contains(RecognizerType.A2_SimpleTex_Turbo))
    }

    @Test fun `availableTypes 同时配置 A1 和 SimpleTex 时返回三条`() {
        val s = RecognizerSettings(
            mathpixAppId = "id", mathpixAppKey = "key",
            simpleTexToken = "tok"
        )
        val available = RecognizerRegistry.availableTypes(s)
        assertEquals("应有三条（A1 + Turbo + Standard）", 3, available.size)
        assertTrue(available.containsAll(listOf(
            RecognizerType.A1_Mathpix,
            RecognizerType.A2_SimpleTex_Turbo,
            RecognizerType.A2_SimpleTex_Standard
        )))
    }

    // ── 3. instantiate —— 按 type 构造实例 ───────────────────────────────

    @Test fun `instantiate 返回 null 当 A1 不可用`() {
        assertNull(RecognizerRegistry.instantiate(RecognizerType.A1_Mathpix, RecognizerSettings()))
    }

    @Test fun `instantiate 返回 null 当 SimpleTex 各类型 token 缺失`() {
        assertNull(RecognizerRegistry.instantiate(
            RecognizerType.A2_SimpleTex_Standard, RecognizerSettings()))
        assertNull(RecognizerRegistry.instantiate(
            RecognizerType.A2_SimpleTex_Turbo, RecognizerSettings()))
    }

    @Test fun `instantiate 返回 MathpixApiRecognizer 当 A1 可用`() {
        val s = RecognizerSettings(mathpixAppId = "id", mathpixAppKey = "key")
        val rec = RecognizerRegistry.instantiate(RecognizerType.A1_Mathpix, s)
        assertNotNull(rec)
        assertTrue("应是 MathpixApiRecognizer 实例", rec is MathpixApiRecognizer)
    }

    @Test fun `instantiate Standard 和 Turbo 都返回 SimpleTexApiRecognizer 实例`() {
        val s = RecognizerSettings(simpleTexToken = "tok")
        val standard = RecognizerRegistry.instantiate(RecognizerType.A2_SimpleTex_Standard, s)
        val turbo = RecognizerRegistry.instantiate(RecognizerType.A2_SimpleTex_Turbo, s)
        assertTrue("Standard 应是 SimpleTexApiRecognizer", standard is SimpleTexApiRecognizer)
        assertTrue("Turbo 应是 SimpleTexApiRecognizer", turbo is SimpleTexApiRecognizer)
        // 两者是不同实例（端点不同）
        assertNotSame("两个实例应不同", standard, turbo)
    }

    // ── 4. resolveLight / resolveDeep —— 按用户绑定解析 ──────────────────

    @Test fun `resolveLight 未绑定时返回 null`() {
        val s = RecognizerSettings(
            lightRecognizerId = null,
            mathpixAppId = "id", mathpixAppKey = "key"
        )
        assertNull(RecognizerRegistry.resolveLight(s))
    }

    @Test fun `resolveLight 绑定 A1 且可用时返回 Mathpix 实例`() {
        val s = RecognizerSettings(
            lightRecognizerId = RecognizerType.A1_Mathpix,
            mathpixAppId = "id", mathpixAppKey = "key"
        )
        assertTrue(RecognizerRegistry.resolveLight(s) is MathpixApiRecognizer)
    }

    @Test fun `resolveLight 绑定 A1 但 Key 被清空时返回 null`() {
        val s = RecognizerSettings(
            lightRecognizerId = RecognizerType.A1_Mathpix,
            mathpixAppId = "", mathpixAppKey = ""
        )
        assertNull("绑定的识别器变为不可用应解析为 null", RecognizerRegistry.resolveLight(s))
    }

    @Test fun `resolveDeep 绑定 A2 Standard 且可用时返回 SimpleTex 实例`() {
        val s = RecognizerSettings(
            deepRecognizerId = RecognizerType.A2_SimpleTex_Standard,
            simpleTexToken = "tok"
        )
        assertTrue(RecognizerRegistry.resolveDeep(s) is SimpleTexApiRecognizer)
    }

    @Test fun `resolveLight 和 resolveDeep 可绑定同类型`() {
        val s = RecognizerSettings(
            lightRecognizerId = RecognizerType.A2_SimpleTex_Turbo,
            deepRecognizerId = RecognizerType.A2_SimpleTex_Turbo,
            simpleTexToken = "tok"
        )
        assertTrue(RecognizerRegistry.resolveLight(s) is SimpleTexApiRecognizer)
        assertTrue(RecognizerRegistry.resolveDeep(s) is SimpleTexApiRecognizer)
    }

    @Test fun `推荐组合 —— Light Turbo + Deep Standard 同 token 双绑定`() {
        // Sprint 1 Task 1.5b 的核心场景：用户填一个 SimpleTex token，
        // 两档分别用 Turbo（高频预览，2000/天）和 Standard（关键识别，500/天）
        val s = RecognizerSettings(
            lightRecognizerId = RecognizerType.A2_SimpleTex_Turbo,
            deepRecognizerId = RecognizerType.A2_SimpleTex_Standard,
            simpleTexToken = "tok"
        )
        assertTrue("Light 应解析为 SimpleTex Turbo",
            RecognizerRegistry.resolveLight(s) is SimpleTexApiRecognizer)
        assertTrue("Deep 应解析为 SimpleTex Standard",
            RecognizerRegistry.resolveDeep(s) is SimpleTexApiRecognizer)
    }

    @Test fun `异构绑定 —— Light A2 Turbo + Deep A1 Mathpix`() {
        val s = RecognizerSettings(
            lightRecognizerId = RecognizerType.A2_SimpleTex_Turbo,
            deepRecognizerId = RecognizerType.A1_Mathpix,
            mathpixAppId = "id", mathpixAppKey = "key",
            simpleTexToken = "tok"
        )
        assertTrue("Light=A2 Turbo", RecognizerRegistry.resolveLight(s) is SimpleTexApiRecognizer)
        assertTrue("Deep=A1", RecognizerRegistry.resolveDeep(s) is MathpixApiRecognizer)
    }

    // ── 5. RecognizerType 枚举属性 ───────────────────────────────────────

    @Test fun `所有 RecognizerType 都有非空 displayName`() {
        for (type in RecognizerType.entries) {
            assertTrue("type=$type 应有 displayName", type.displayName.isNotBlank())
        }
    }

    @Test fun `所有 RecognizerType 都有非空 description`() {
        for (type in RecognizerType.entries) {
            assertTrue("type=$type 应有 description", type.description.isNotBlank())
        }
    }
}
