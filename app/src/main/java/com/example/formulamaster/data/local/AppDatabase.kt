package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.OcrFeedbackDao
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.OcrFeedbackEntity
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.local.entity.StudyStateEntity

@Database(
    entities = [
        FormulaEntity::class,
        StudyStateEntity::class,
        ReviewLogEntity::class,
        OcrFeedbackEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun formulaDao(): FormulaDao
    abstract fun studyStateDao(): StudyStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun ocrFeedbackDao(): OcrFeedbackDao

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
