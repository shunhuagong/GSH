package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 某一天是训练日还是休息日，按日期记录。
 */
@Entity(tableName = "day_types")
data class DayType(
    @PrimaryKey val date: String,
    val isTraining: Boolean
)

@Dao
interface DayTypeDao {

    @Query("SELECT * FROM day_types WHERE date = :date")
    fun observeByDate(date: String): Flow<DayType?>

    @Query("SELECT * FROM day_types")
    fun observeAll(): Flow<List<DayType>>

    @Query("SELECT * FROM day_types ORDER BY date")
    suspend fun all(): List<DayType>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dayType: DayType)
}
