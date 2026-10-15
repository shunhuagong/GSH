package com.gsh.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

/**
 * 数据仓库：统一入口，负责数据库 DAO 与用户设置的访问、预置食物的初始化。
 */
class GshRepository(context: Context) {

    private val db = AppDatabase.get(context)
    val foodDao: FoodDao = db.foodDao()
    val mealEntryDao: MealEntryDao = db.mealEntryDao()
    val dailyTotalDao: DailyTotalDao = db.dailyTotalDao()
    val dayTypeDao: DayTypeDao = db.dayTypeDao()
    val settings: SettingsStore = SettingsStore(context)
    val searchHistory: SearchHistoryStore = SearchHistoryStore(context)

    /** 导出文件的缓存目录（分享后系统会自动清理）。 */
    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun setDayType(date: String, isTraining: Boolean) {
        dayTypeDao.upsert(DayType(date, isTraining))
    }

    /**
     * 按名字同步预置食物库：
     * 1. 补插种子清单中缺失的条目（如新增食物、肉类拆分生熟后的新条目）；
     * 2. 清理已不在种子清单中的旧预置条目（如被生熟拆分替代的旧口径"鸡胸肉"）。
     * 不影响自定义食物；历史记录保存营养快照，不受条目变动影响。
     */
    suspend fun seedFoods() {
        val seedNames = SeedFoods.list.map { it.name }.toHashSet()
        val existing = foodDao.allNames().toHashSet()

        val missing = SeedFoods.list.filter { it.name !in existing }
        if (missing.isNotEmpty()) {
            foodDao.insertAll(missing)
        }

        val stale = (existing - seedNames).toList()
        if (stale.isNotEmpty()) {
            foodDao.deleteNonCustom(stale)
        }
    }

    suspend fun addEntry(food: Food, amount: Double, mealType: Int, date: String) {
        val snapshot = food.nutrition(amount)
        mealEntryDao.insert(
            MealEntry(
                date = date,
                mealType = mealType,
                foodId = food.id,
                foodName = food.name,
                amount = amount,
                unit = food.unit,
                protein = snapshot.protein,
                carbs = snapshot.carbs,
                fat = snapshot.fat
            )
        )
    }

    /** 修改一条记录的数量：营养快照按新旧数量比例折算，保持记录口径一致。 */
    suspend fun updateEntryAmount(entry: MealEntry, amount: Double) {
        if (amount <= 0.0 || entry.amount <= 0.0) return
        val scale = amount / entry.amount
        mealEntryDao.update(
            entry.copy(
                amount = amount,
                protein = entry.protein * scale,
                carbs = entry.carbs * scale,
                fat = entry.fat * scale
            )
        )
    }

    /** 把 from 日的全部记录复制到 to 日（保留餐别与营养快照），返回复制条数。 */
    suspend fun copyDay(from: String, to: String): Int {
        val source = mealEntryDao.byDate(from)
        if (source.isEmpty()) return 0
        mealEntryDao.insertAll(source.map { it.copy(id = 0, date = to) })
        return source.size
    }

    /** 导出全部饮食记录为 CSV（带 BOM，Excel 可直接打开）。 */
    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder("\uFEFF")
        sb.append("日期,餐别,食物,数量,单位,蛋白(g),碳水(g),脂肪(g),热量(kcal)\n")
        mealEntryDao.allEntries().forEach { e ->
            sb.append(e.date).append(',')
                .append(MealEntry.mealName(e.mealType)).append(',')
                .append(csvField(e.foodName)).append(',')
                .append(num(e.amount)).append(',')
                .append(e.amountLabel).append(',')
                .append(fixed(e.protein)).append(',')
                .append(fixed(e.carbs)).append(',')
                .append(fixed(e.fat)).append(',')
                .append("%.0f".format(e.calories)).append('\n')
        }
        File(exportDir, "gsh_export.csv").apply {
            writeText(sb.toString(), Charsets.UTF_8)
        }
    }

    /** 导出全部数据为 JSON：身体信息、训练/休息日标记、全部饮食记录。 */
    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val profile = settings.profile.value
        val json = JSONObject().apply {
            put("app", "GSH")
            put("format", 1)
            put("exportedAt", LocalDateTime.now().toString())
            put("profile", JSONObject().apply {
                put("weightKg", profile.weightKg)
                put("proteinPerKg", profile.proteinPerKg)
                put("carbsTrainingPerKg", profile.carbsTrainingPerKg)
                put("carbsRestPerKg", profile.carbsRestPerKg)
                put("fatPerKg", profile.fatPerKg)
            })
            put("dayTypes", JSONArray().apply {
                dayTypeDao.all().forEach { d ->
                    put(JSONObject().put("date", d.date).put("isTraining", d.isTraining))
                }
            })
            put("entries", JSONArray().apply {
                mealEntryDao.allEntries().forEach { e ->
                    put(JSONObject().apply {
                        put("date", e.date)
                        put("mealType", e.mealType)
                        put("foodName", e.foodName)
                        put("amount", e.amount)
                        put("unit", e.unit)
                        put("protein", e.protein)
                        put("carbs", e.carbs)
                        put("fat", e.fat)
                    })
                }
            })
        }
        File(exportDir, "gsh_export.json").apply {
            writeText(json.toString(2), Charsets.UTF_8)
        }
    }

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    /** 数量去掉多余的 .0，如 2.0 → 2 */
    private fun num(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    /** CSV 数值固定用点号小数，避免不同语言环境产生逗号 */
    private fun fixed(value: Double): String = String.format(Locale.US, "%.1f", value)

    companion object {
        @Volatile
        private var instance: GshRepository? = null

        fun get(context: Context): GshRepository =
            instance ?: synchronized(this) {
                instance ?: GshRepository(context.applicationContext).also { instance = it }
            }
    }
}
