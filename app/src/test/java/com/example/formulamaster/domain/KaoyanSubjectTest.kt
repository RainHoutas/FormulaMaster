package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sprint 1 Task 1.8：KaoyanSubject enum 单测（fromName / fromCode / Default 兜底）。
 */
class KaoyanSubjectTest {

    @Test
    fun `entries 覆盖三个子科目`() {
        assertEquals(3, KaoyanSubject.entries.size)
        val codes = KaoyanSubject.entries.map { it.code }.toSet()
        assertEquals(setOf("1", "2", "3"), codes)
    }

    @Test
    fun `Default 为 Type1（数一）`() {
        assertEquals(KaoyanSubject.Type1, KaoyanSubject.Default)
    }

    @Test
    fun `fromName 正常解析三个枚举名`() {
        assertEquals(KaoyanSubject.Type1, KaoyanSubject.fromName("Type1"))
        assertEquals(KaoyanSubject.Type2, KaoyanSubject.fromName("Type2"))
        assertEquals(KaoyanSubject.Type3, KaoyanSubject.fromName("Type3"))
    }

    @Test
    fun `fromName null 或未知值兜底 Default`() {
        assertEquals(KaoyanSubject.Default, KaoyanSubject.fromName(null))
        assertEquals(KaoyanSubject.Default, KaoyanSubject.fromName(""))
        assertEquals(KaoyanSubject.Default, KaoyanSubject.fromName("Type9"))
        assertEquals(KaoyanSubject.Default, KaoyanSubject.fromName("garbage"))
    }

    @Test
    fun `fromCode 正反双向匹配 落库短码`() {
        assertEquals(KaoyanSubject.Type1, KaoyanSubject.fromCode("1"))
        assertEquals(KaoyanSubject.Type2, KaoyanSubject.fromCode("2"))
        assertEquals(KaoyanSubject.Type3, KaoyanSubject.fromCode("3"))
        // 未知 code 返回 null（区别于 fromName 的 Default 兜底）
        assertNull(KaoyanSubject.fromCode("4"))
        assertNull(KaoyanSubject.fromCode(null))
        assertNull(KaoyanSubject.fromCode(""))
    }
}
