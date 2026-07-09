package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.formulamaster.data.local.dao.BlockedFormulaDao
import com.example.formulamaster.data.local.dao.EntryRelationDao
import com.example.formulamaster.data.local.dao.EntryTagDao
import com.example.formulamaster.data.local.dao.ErrorReportDao
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.OcrFeedbackDao
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.ReviewSessionProgressDao
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.dao.SubCardStateDao
import com.example.formulamaster.data.local.dao.TagDao
import com.example.formulamaster.data.local.entity.BlockedFormulaEntity
import com.example.formulamaster.data.local.entity.EntryRelationEntity
import com.example.formulamaster.data.local.entity.EntryTagCrossRef
import com.example.formulamaster.data.local.entity.ErrorReportEntity
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.OcrFeedbackEntity
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.local.entity.ReviewSessionProgressEntity
import com.example.formulamaster.data.local.entity.StudyStateEntity
import com.example.formulamaster.data.local.entity.SubCardStateEntity
import com.example.formulamaster.data.local.entity.TagEntity

@Database(
    entities = [
        FormulaEntity::class,
        StudyStateEntity::class,
        ReviewLogEntity::class,
        OcrFeedbackEntity::class,
        SubCardStateEntity::class,
        ErrorReportEntity::class,
        BlockedFormulaEntity::class,
        ReviewSessionProgressEntity::class,
        // Sprint 4 Task 4.1 数据层地基标签化（取代 FormulaSubjectMapEntity）
        TagEntity::class,
        EntryTagCrossRef::class,
        EntryRelationEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun formulaDao(): FormulaDao
    abstract fun studyStateDao(): StudyStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun ocrFeedbackDao(): OcrFeedbackDao
    abstract fun subCardStateDao(): SubCardStateDao
    abstract fun errorReportDao(): ErrorReportDao
    abstract fun blockedFormulaDao(): BlockedFormulaDao
    abstract fun reviewSessionProgressDao(): ReviewSessionProgressDao
    // Sprint 4 Task 4.1 标签地基
    abstract fun tagDao(): TagDao
    abstract fun entryTagDao(): EntryTagDao
    abstract fun entryRelationDao(): EntryRelationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "formula_master.db"
                )
                    // Sprint 1 Task 1.9：v1 → v2 加 ocr_feedback 表。
                    // Sprint 3 Task 3.4：v2 → v3 改 ocr_feedback schema：
                    //   - correctLatex 改 nullable（旧字段，新流程不再写）
                    //   - 新增 wrongPlaceholdersJson（用户多选错误部件的 placeholder 列表 JSON）
                    // 学习流程重构 Sprint 1 Task 1.2：v3 → v4 扩 FormulaEntity schema：
                    //   - 加 purpose / preconditions / parents / siblings / confusableWith /
                    //     typicalProblems / commonErrors / mnemonic / examWeight / scene
                    //   - derivationSteps 格式重写为 [{latex, note}, ...] 对象数组
                    // 学习流程重构 Sprint 1 Task 1.3：v4 → v5 新增 formula_subject_map 表
                    //   - 多对多关系：公式 ↔ 数一/数二/数三 子科目
                    //   - 外键级联：FormulaEntity 删除时同步清理映射行
                    // 学习流程重构 Sprint 1 Task 1.5：v5 → v6 新增 sub_card_states 表
                    //   - 子卡级 FSRS 状态，复合主键 (formulaId, cardType)
                    //   - 母 study_states：Task 2.6（2026-05-29）已退役，仅保留表兼容老库，全部读写迁到 sub_card_states
                    // 学习流程重构 Sprint 1 Task 1.6：v6 → v7 新增 error_reports 表
                    //   - 错题反向链路记录；createdAt 索引便于错题本按时间倒序展示
                    //   - 写入由 ErrorReportProcessor 统一触发（同步 SubCardState 砍半 + 推次日）
                    // 学习流程重构 Sprint 2 Task 2.1：v7 → v8 SubCardStateEntity 加 isReinforced 字段
                    //   - 强标记（加强卡回考再失败的跨会话标记），详 RFC §9.3 D-S2-2 补充第 5 条
                    //   - destructiveMigration 重置；旧 FSRS 进度由 seedIfEmpty 重新铺底
                    // 学习流程重构 Sprint 2 Task 2.1b：v8 → v9 新增 blocked_formulas 表
                    //   - 公式级跨会话 blocked 状态；UI 据此在默写界面顶部展示红色强提醒
                    //   - CASCADE 删除：FormulaEntity 删除时同步清理
                    // 学习流程重构 Sprint 2 Task 2.1b：v9 → v10 新增 review_session_progress 单行表
                    //   - 同日 cursor 续接（跨日由 ViewModel 比较 sessionDateMs 决定丢弃 / 续接）
                    //   - 序列化由 ReviewSessionProgressCodec 处理（扁平 DTO 绕开 DictationState sealed）
                    // 学习流程重构 Sprint 3 Task 3.3：v10 → v11 error_reports 加 penaltySnapshotJson 字段
                    //   - 录入错题时快照所选公式各子卡 (S/nextReview/lapses) 原值
                    //   - 删除时选「恢复计划」→ 逐子卡 best-effort 还原（未被真实复习触碰过的才还原）
                    // 学习流程重构 Sprint 4 Task 4.1：v11 → v12 数据层地基标签化（RFC §9.4 D16）
                    //   - 新增 tags / entry_tag_map / entry_relations 三表（分类唯一真相源 + 关系边表）
                    //   - 退休 formula_subject_map：数一二三并入 namespace=exam 标签
                    //   - FormulaEntity 移除 parents/siblings/confusableWith（迁 entry_relations）
                    //   - 词典「外壳」愿景地基：加任意新分类维度只需新 namespace，无需改 schema
                    // 打磨阶段仍允许重置数据，用户基础数据由 assets/formulas.json 在首次启动重新预加载，
                    // FSRS 进度量小重新激活成本可接受；避免维护手写 Migration 的工程开销。
                    // 已收集的反馈样本会随升级清空——属预期行为。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
