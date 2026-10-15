package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealEntryDao {

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY mealType, id")
    fun observeByDate(date: String): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE date = :date")
    suspend fun byDate(date: String): List<MealEntry>

    @Query("SELECT * FROM meal_entries ORDER BY date, mealType, id")
    suspend fun allEntries(): List<MealEntry>

    @Insert
    suspend fun insert(entry: MealEntry): Long

    @Insert
    suspend fun insertAll(entries: List<MealEntry>)

    @Update
    suspend fun update(entry: MealEntry)

    @Delete
    suspend fun delete(entry: MealEntry)
}

/**
 * 按天聚合的营养总量（历史页使用）。
 */
data class DailyTotal(
    val date: String,
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    val calories: Double
        get() = protein * 4 + carbs * 4 + fat * 9
}

@Dao
interface DailyTotalDao {

    @Query(
        "SELECT date, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat " +
            "FROM meal_entries GROUP BY date ORDER BY date DESC"
    )
    fun observeDailyTotals(): Flow<List<DailyTotal>>
}
