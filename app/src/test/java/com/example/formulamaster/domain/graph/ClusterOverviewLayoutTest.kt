package com.example.formulamaster.domain.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 4 Task 4.2：母层聚类分区布局校验——确定性 + 学科分带 + 气泡随公式数增长。
 */
class ClusterOverviewLayoutTest {

    private val subjects = listOf("高数", "线代", "概率论")
    private fun clusters() = listOf(
        ChapterCluster("高数", "极限与连续", 4),
        ChapterCluster("高数", "微分中值定理", 3),
        ChapterCluster("高数", "一元函数积分学", 6),
        ChapterCluster("线代", "行列式", 1),
        ChapterCluster("线代", "矩阵", 1),
        ChapterCluster("概率论", "条件概率", 2)
    )

    @Test
    fun `确定性——同输入两次布局像素级一致`() {
        val a = ClusterOverviewLayout.layout(clusters(), subjects)
        val b = ClusterOverviewLayout.layout(clusters(), subjects)
        assertEquals(a.bubbles, b.bubbles)
        assertEquals(a.world, b.world)
    }

    @Test
    fun `每个章节簇都有气泡坐标`() {
        val r = ClusterOverviewLayout.layout(clusters(), subjects)
        clusters().forEach { c -> assertTrue("[${c.chapter}] 应有气泡", r.bubbles.containsKey(c.key)) }
        assertEquals(6, r.bubbles.size)
    }

    @Test
    fun `学科竖直分带——高数全部在线代之上、线代在概率之上`() {
        val r = ClusterOverviewLayout.layout(clusters(), subjects)
        fun ys(s: String) = r.bubbles.filterKeys { it.subject == s }.values.map { it.center.y }
        val gao = ys("高数"); val xian = ys("线代"); val gai = ys("概率论")
        assertTrue("高数应整体在线代之上", gao.max() < xian.min())
        assertTrue("线代应整体在概率之上", xian.max() < gai.min())
    }

    @Test
    fun `气泡半径随公式数增长`() {
        val r = ClusterOverviewLayout.layout(clusters(), subjects)
        val big = r.bubbles.getValue(ChapterKey("高数", "一元函数积分学")).radius   // 6 条
        val small = r.bubbles.getValue(ChapterKey("线代", "行列式")).radius          // 1 条
        assertTrue("公式多的章节气泡更大", big > small)
    }

    @Test
    fun `世界包围盒为正 且容纳所有气泡`() {
        val r = ClusterOverviewLayout.layout(clusters(), subjects)
        assertTrue(r.world.w > 0 && r.world.h > 0)
        r.bubbles.values.forEach { b ->
            assertTrue("气泡应在世界内", b.center.x in 0f..r.world.w && b.center.y in 0f..r.world.h)
        }
    }

    @Test
    fun `未在 subjectOrder 的学科稳定追加不崩`() {
        val cs = clusters() + ChapterCluster("政治", "马原", 2)
        val r = ClusterOverviewLayout.layout(cs, subjects)   // subjectOrder 不含"政治"
        assertTrue(r.bubbles.containsKey(ChapterKey("政治", "马原")))
    }

    @Test
    fun `空输入返回空布局不崩`() {
        val r = ClusterOverviewLayout.layout(emptyList(), subjects)
        assertTrue(r.bubbles.isEmpty())
    }
}
