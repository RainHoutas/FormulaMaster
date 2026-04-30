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
    version = 2,
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
                    // Sprint 1 Task 1.9：v1 → v2 加 ocr_feedback 表。打磨阶段仍允许重置数据，
                    // 用户基础数据由 assets/formulas.json 在首次启动重新预加载，FSRS 进度
                    // 量小重新激活成本可接受；避免维护手写 Migration 的工程开销
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
