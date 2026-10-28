package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 体重记录：一天一条（重复记录当天覆盖）。
 * 记录同时会同步更新设置里的体重，营养目标随之变化。
 */
@Entity(tableName = "weight_logs")
data class WeightLog(
    @PrimaryKey val date: String,   // yyyy-MM-dd
    val weightKg: Double,
    val createdAt: Long
)

@Dao
interface WeightLogDao {

    /** 全部体重记录，旧日期在前。 */
    @Query("SELECT * FROM weight_logs ORDER BY date")
    fun observeAll(): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WeightLog)
}
