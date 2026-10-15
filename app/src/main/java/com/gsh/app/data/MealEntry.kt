package com.gsh.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条饮食记录。保存食物名称、数量单位和营养快照，
 * 这样即使食物之后被修改或删除，历史记录依然准确。
 */
@Entity(tableName = "meal_entries", indices = [Index("date")])
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,      // yyyy-MM-dd
    val mealType: Int,     // 0早餐 1午餐 2晚餐 3加餐
    val foodId: Long,
    val foodName: String,
    val amount: Double,    // 克数 / 毫升数 / 个数
    val unit: Int,         // 单位快照：Food.UNIT_*
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    val calories: Double
        get() = protein * 4 + carbs * 4 + fat * 9

    /** 展示用数量单位 */
    val amountLabel: String
        get() = when (unit) {
            Food.UNIT_ML -> "ml"
            Food.UNIT_PIECE -> "个"
            else -> "g"
        }

    /** 输入数量对应的单位提示：克数 / 毫升 / 数量 */
    val amountHint: String
        get() = when (unit) {
            Food.UNIT_ML -> "毫升（ml）"
            Food.UNIT_PIECE -> "数量（个）"
            else -> "克数"
        }

    companion object {
        const val MEAL_BREAKFAST = 0
        const val MEAL_LUNCH = 1
        const val MEAL_DINNER = 2
        const val MEAL_SNACK = 3
        const val MEAL_COUNT = 4

        fun mealName(type: Int): String = when (type) {
            MEAL_BREAKFAST -> "早餐"
            MEAL_LUNCH -> "午餐"
            MEAL_DINNER -> "晚餐"
            MEAL_SNACK -> "加餐"
            else -> "其他"
        }
    }
}
