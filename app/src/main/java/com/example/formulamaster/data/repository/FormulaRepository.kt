package com.example.formulamaster.data.repository

import android.content.Context
import com.example.formulamaster.data.local.dao.FormulaDao
import com.example.formulamaster.data.local.entity.FormulaEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FormulaRepository(
    private val context: Context,
    private val formulaDao: FormulaDao
) {

    fun getAll(): Flow<List<FormulaEntity>> = formulaDao.getAll()

    suspend fun getById(id: String): FormulaEntity? = formulaDao.getById(id)

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (formulaDao.count() > 0) return@withContext
        val json = context.assets.open("formulas.json").bufferedReader().readText()
        val type = object : TypeToken<List<FormulaEntity>>() {}.type
        val formulas: List<FormulaEntity> = Gson().fromJson(json, type)
        formulaDao.insertAll(formulas)
    }
}
