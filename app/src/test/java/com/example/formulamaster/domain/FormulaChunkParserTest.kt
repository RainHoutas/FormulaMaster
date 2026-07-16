package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaChunkParserTest {

    @Test fun `空串返回空列表`() =
        assertTrue(FormulaChunkParser.parse("").isEmpty())

    @Test fun `空数组返回空列表`() =
        assertTrue(FormulaChunkParser.parse("[]").isEmpty())

    @Test fun `正常解析 latex 与 note`() {
        val chunks = FormulaChunkParser.parse(
            """[{"latex":"P(A)","note":"目标概率"},{"latex":"","note":"纯文字块"}]"""
        )
        assertEquals(2, chunks.size)
        assertEquals("P(A)", chunks[0].latex)
        assertEquals("目标概率", chunks[0].note)
        assertEquals("", chunks[1].latex) // 允许空 latex
    }

    @Test fun `非法 JSON 返回空列表不抛`() =
        assertTrue(FormulaChunkParser.parse("不是数组").isEmpty())
}
