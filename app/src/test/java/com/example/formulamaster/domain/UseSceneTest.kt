package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 1 Task 1.8：UseScene enum 单测（覆盖 + Default + displayName 非空 ）。
 */
class UseSceneTest {

    @Test
    fun `entries 覆盖三个场景`() {
        assertEquals(3, UseScene.entries.size)
        val names = UseScene.entries.map { it.name }.toSet()
        assertEquals(setOf("KaoyanMath", "Gaokao", "SelfStudy"), names)
    }

    @Test
    fun `Default 为 KaoyanMath（当前阶段唯一实装场景）`() {
        assertEquals(UseScene.KaoyanMath, UseScene.Default)
    }

    @Test
    fun `各场景 displayName 与 description 非空`() {
        UseScene.entries.forEach {
            assertTrue("${it.name} displayName 应非空", it.displayName.isNotBlank())
            assertTrue("${it.name} description 应非空", it.description.isNotBlank())
        }
    }
}
