package com.example.formulamaster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.dao.ReviewLogDao
import com.example.formulamaster.data.local.dao.StudyStateDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.example.formulamaster.data.local.entity.ReviewLogEntity
import com.example.formulamaster.data.local.entity.StudyStateEntity

@Database(
    entities = [
        FormulaEntity::class,
        StudyStateEntity::class,
        ReviewLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun formulaDao(): FormulaDao
    abstract fun studyStateDao(): StudyStateDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "formula_master.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
