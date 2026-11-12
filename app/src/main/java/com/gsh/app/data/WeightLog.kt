package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 身体记录（原体重记录）：一天一条（重复记录当天覆盖）。
 * 体重 kg、骨骼肌 kg（体重必填；体脂率 %、骨骼肌 kg 可选，没测为 null，禁止存 0）。
 * 与设置页 profile 体重是两回事：本表只做记录与趋势展示，不同步营养目标。
 */
@Entity(tableName = "weight_logs")
data class WeightLog(
    @PrimaryKey val date: String,   // yyyy-MM-dd
    val weightKg: Double,
    val createdAt: Long,
    val bodyFat: Double? = null,     // 体脂率 %
    val muscleKg: Double? = null     // 骨骼肌 kg
)

@Dao
interface WeightLogDao {

    /** 全部体重记录，旧日期在前。 */
    @Query("SELECT * FROM weight_logs ORDER BY date")
    fun observeAll(): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WeightLog)
}
