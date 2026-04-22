package com.example.formulamaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 静态公式库表（formulas）
 * 数据由 assets/formulas.json 在首次启动时预加载，运行期只读。
 */
@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey val formulaId: String,          // 唯一标识符，如 "calc_taylor_01"
    val subject: String,                        // 科目：高数 / 线代 / 概率论
    val chapter: String,                        // 章节，如 "一元函数微积分"
    val title: String,                          // 公式名称，如 "泰勒展开式"
    val latexCode: String,                      // 完整 LaTeX 源码
    val clozeData: String,                      // JSON：填空挖空坐标/片段（见 ClozeItem）
    val derivationSteps: String,                // JSON：推导过程片段数组
    val tags: String,                           // 应用场景标签，逗号分隔或 JSON
    val difficultyLevel: Int                    // 客观难度评级 1-5，用于初次激活时权重
)
