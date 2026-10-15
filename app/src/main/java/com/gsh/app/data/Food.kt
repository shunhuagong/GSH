package com.gsh.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 食物。营养含量为"单位基准"下的克数：
 * - unit = UNIT_GRAM / UNIT_ML：每 100g / 100ml 的含量
 * - unit = UNIT_PIECE：每个的含量
 */
@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val proteinPerUnit: Double,
    val carbsPerUnit: Double,
    val fatPerUnit: Double,
    val unit: Int = UNIT_GRAM,
    val isCustom: Boolean = false
) {
    val caloriesPerUnit: Double
        get() = proteinPerUnit * 4 + carbsPerUnit * 4 + fatPerUnit * 9

    /** 展示单位：g / ml / 个 */
    val unitLabel: String
        get() = when (unit) {
            UNIT_ML -> "ml"
            UNIT_PIECE -> "个"
            else -> "g"
        }

    /** 营养基准描述：每100g / 每100ml / 每个 */
    val basisLabel: String
        get() = when (unit) {
            UNIT_ML -> "每100ml"
            UNIT_PIECE -> "每个"
            else -> "每100g"
        }

    /** 输入数量对应的单位提示：克数 / 毫升 / 数量 */
    val amountHint: String
        get() = when (unit) {
            UNIT_ML -> "毫升（ml）"
            UNIT_PIECE -> "数量（个）"
            else -> "克数"
        }

    /**
     * 按输入数量换算营养。amount 对克/毫升是数值本身，对"个"是个数。
     */
    fun nutrition(amount: Double): NutritionSnapshot = when (unit) {
        UNIT_PIECE -> NutritionSnapshot(
            protein = proteinPerUnit * amount,
            carbs = carbsPerUnit * amount,
            fat = fatPerUnit * amount
        )
        else -> NutritionSnapshot(
            protein = proteinPerUnit * amount / 100.0,
            carbs = carbsPerUnit * amount / 100.0,
            fat = fatPerUnit * amount / 100.0
        )
    }

    companion object {
        const val UNIT_GRAM = 0
        const val UNIT_ML = 1
        const val UNIT_PIECE = 2

        fun unitName(unit: Int): String = when (unit) {
            UNIT_ML -> "毫升"
            UNIT_PIECE -> "个"
            else -> "克"
        }
    }
}

/**
 * 一份食物按数量换算后的营养值。
 */
data class NutritionSnapshot(
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    val calories: Double
        get() = protein * 4 + carbs * 4 + fat * 9
}
