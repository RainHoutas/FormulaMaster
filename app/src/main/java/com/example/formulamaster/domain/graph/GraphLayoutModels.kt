package com.example.formulamaster.domain.graph

/**
 * 公式族图谱 · 布局层数据模型（Sprint 4 Task 4.2，详 RFC §9.4 D17）。
 *
 * 原子化三层引擎的**布局层**接口数据：布局纯函数只吃「结构」（章节簇 + 关系边），
 * 吐「坐标」，不感知 Room / Compose / 学习状态（那些是数据层与渲染层的事）。
 * 坐标为无量纲**世界坐标**，渲染层再套相机变换缩放平移。
 *
 * 两级布局对应母子结构：
 * - 母层 [ClusterOverviewLayout]：章节簇 → 气泡位置（聚类分区）
 * - 子层 [WithinChapterLayout]：单章节内公式 → 分层坐标（推导纵向）
 */
data class GraphPoint(val x: Float, val y: Float)

/** 世界矩形（左上原点 + 宽高），母层画章节分区背景 / 定相机边界用。 */
data class GraphRect(val x: Float, val y: Float, val w: Float, val h: Float)

/** 章节标识（学科 + 章节），母层气泡与子层归属的键。 */
data class ChapterKey(val subject: String, val chapter: String)

/** 母层输入：一个章节簇（学科 + 章节 + 公式数）。公式数决定气泡大小。 */
data class ChapterCluster(
    val subject: String,
    val chapter: String,
    val nodeCount: Int
) {
    val key: ChapterKey get() = ChapterKey(subject, chapter)
}

/** 母层单个气泡的布局结果。 */
data class BubbleLayout(val center: GraphPoint, val radius: Float)

/** 母层布局结果：各章节气泡 + 世界包围盒（供相机初始定位 / 拖动边界）。 */
data class OverviewLayout(
    val bubbles: Map<ChapterKey, BubbleLayout>,
    val world: GraphRect
)

/**
 * 推导边（子层块内分层用）。方向与 `entry_relations` 一致：
 * [childId] 由 [parentId] 推导得到（parentId 是上游 / 父，层更小 / 更靠上）。
 */
data class DerivationEdge(val childId: String, val parentId: String)
