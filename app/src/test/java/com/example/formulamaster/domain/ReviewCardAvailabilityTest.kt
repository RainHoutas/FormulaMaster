package com.example.formulamaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCardAvailabilityTest {

    /** 全字段齐备 + 同章池充足 → 六卡全出。 */
    private val full = ReviewCardAvailability.FormulaData(
        hasCloze = true,
        hasPreconditions = true,
        hasDerivation = true,
        hasPurpose = true,
        hasConfusable = true,
        hasTypicalProblems = true,
        chapterPoolSize = 3,
    )

    @Test fun `C1 识别恒可出（只需公式本体）`() {
        // 即便其它数据全空、单章独苗，C1 仍出
        val empty = ReviewCardAvailability.FormulaData(
            hasCloze = false, hasPreconditions = false, hasDerivation = false,
            hasPurpose = false, hasConfusable = false, hasTypicalProblems = false,
            chapterPoolSize = 1,
        )
        assertTrue(ReviewCardAvailability.isAvailable(CardType.C1_Recognition, empty))
    }

    @Test fun `全数据齐备则六卡全出`() {
        assertEquals(CardType.entries.toSet(), ReviewCardAvailability.availableCards(full))
    }

    @Test fun `无 cloze 则 C2 不出`() =
        assertFalse(ReviewCardAvailability.isAvailable(CardType.C2_Cloze, full.copy(hasCloze = false)))

    @Test fun `无 preconditions 则 C3 不出`() =
        assertFalse(ReviewCardAvailability.isAvailable(CardType.C3_Precondition, full.copy(hasPreconditions = false)))

    @Test fun `无 derivation 则 C4 不出`() =
        assertFalse(ReviewCardAvailability.isAvailable(CardType.C4_Derivation, full.copy(hasDerivation = false)))

    @Test fun `C5 需易混邻居且用途非空`() {
        assertFalse("无易混邻居不出", ReviewCardAvailability.isAvailable(CardType.C5_Discrimination, full.copy(hasConfusable = false)))
        assertFalse("用途空无题干线索不出", ReviewCardAvailability.isAvailable(CardType.C5_Discrimination, full.copy(hasPurpose = false)))
        assertTrue("两者齐备才出", ReviewCardAvailability.isAvailable(CardType.C5_Discrimination, full))
    }

    @Test fun `C6 需同章池至少2且有题面`() {
        assertFalse("独苗无干扰项不出", ReviewCardAvailability.isAvailable(CardType.C6_TypicalProblem, full.copy(chapterPoolSize = 1)))
        assertFalse("无题面不出", ReviewCardAvailability.isAvailable(CardType.C6_TypicalProblem, full.copy(hasTypicalProblems = false)))
        assertTrue("两者齐备才出", ReviewCardAvailability.isAvailable(CardType.C6_TypicalProblem, full.copy(chapterPoolSize = 2)))
    }

    @Test fun `只有公式本体（其余全空）则仅剩 C1`() {
        val onlyBody = ReviewCardAvailability.FormulaData(
            hasCloze = false, hasPreconditions = false, hasDerivation = false,
            hasPurpose = false, hasConfusable = false, hasTypicalProblems = false,
            chapterPoolSize = 5,   // 有同章池但无题面 → C6 仍不出
        )
        assertEquals(setOf(CardType.C1_Recognition), ReviewCardAvailability.availableCards(onlyBody))
    }
}
