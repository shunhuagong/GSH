package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 训练动作：内置 + 自定义。
 * recordType 在新建时规定记录方式：0=重量×次数（器械），1=纯次数（自重），
 * 2=计时时长，-1=未设置（存量待推断；首次录入时按所填实时判定并写回，永久固定）。
 */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,   // 腿 / 背 / 胸 / 肩 / 臂 / 核心
    val isCustom: Boolean = false,
    val recordType: Int = -1
) {
    companion object {
        const val TYPE_STRENGTH = 0   // 重量×次数
        const val TYPE_REPS = 1        // 纯次数
        const val TYPE_DURATION = 2    // 计时时长
        const val TYPE_UNSET = -1      // 未设置
    }
}

/**
 * 一条训练记录（对齐饮食记录的逐条添加模式）：
 * 器械组次型填重量+次数；计时型动作（平板支撑/跑步等）填时长分钟。
 * 同一天的若干条 = 一次训练。
 */
@Entity(
    tableName = "training_entries",
    indices = [Index("date"), Index("exerciseId")]
)
data class TrainingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val exerciseId: Long,
    val weightKg: Double?,      // 器械型：重量
    val reps: Int?,            // 器械型：次数
    val durationMin: Int?,      // 计时型：时长（分钟）
    val createdAt: Long
) {
    /** 列表显示文案："60 kg × 12" / "20 个"（纯次数）/ "15 分钟" */
    val display: String
        get() = when {
            weightKg != null && reps != null -> trimNum(weightKg) + " kg × $reps"
            reps != null -> "$reps 个"
            durationMin != null -> "$durationMin 分钟"
            else -> ""
        }
}

/** 「我」页最近一次训练摘要（一天一行聚合）。 */
data class TrainingSummary(
    val date: String,
    val entryCount: Int,
    val exerciseCount: Int
)

/** 看板用：某动作的全部记录（按日升序，内存聚合每日最好一组）。 */
data class ExerciseCount(
    val exerciseId: Long,
    val cnt: Int
)

private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY id")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("UPDATE exercises SET recordType = :recordType WHERE id = :id")
    suspend fun updateRecordType(id: Long, recordType: Int)
}

/** 存量回填用：各动作历史记录里哪类字段非空，据此推断 recordType。 */
data class ExerciseMetric(
    val exerciseId: Long,
    val hasWeight: Int,
    val hasReps: Int,
    val hasDuration: Int
)

@Dao
interface TrainingEntryDao {

    @Query("SELECT * FROM training_entries WHERE date = :date ORDER BY id")
    fun observeByDate(date: String): Flow<List<TrainingEntry>>

    @Insert
    suspend fun insert(entry: TrainingEntry): Long

    /**
     * 指定日期之前、该动作最近一个有训练日的全部组（补录过去日期的带过用，
     * 严格 < before，不受其后的记录污染；该日期之前无记录返回空）。
     */
    @Query(
        "SELECT * FROM training_entries WHERE exerciseId = :exerciseId " +
            "AND date = (SELECT MAX(date) FROM training_entries " +
            "WHERE exerciseId = :exerciseId AND date < :before) " +
            "ORDER BY id"
    )
    suspend fun lastSetsOfBefore(exerciseId: Long, before: String): List<TrainingEntry>

    @Query("DELETE FROM training_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT date, COUNT(*) as entryCount, COUNT(DISTINCT exerciseId) as exerciseCount " +
            "FROM training_entries GROUP BY date ORDER BY date DESC LIMIT 1"
    )
    fun observeLatestSummary(): Flow<TrainingSummary?>

    /** 某动作的全部记录（看板内存聚合用，按日升序）。 */
    @Query("SELECT * FROM training_entries WHERE exerciseId = :exerciseId ORDER BY date, id")
    fun observeByExercise(exerciseId: Long): Flow<List<TrainingEntry>>

    /** 各动作的记录条数（看板动作条：只列出有记录的动作，条数多的排前）。 */
    @Query("SELECT exerciseId, COUNT(*) as cnt FROM training_entries GROUP BY exerciseId ORDER BY cnt DESC")
    fun observeExerciseCounts(): Flow<List<ExerciseCount>>

    /** 各动作历史记录的字段分布（recordType 存量回填推断用）。 */
    @Query(
        "SELECT exerciseId, " +
            "MAX(CASE WHEN weightKg IS NOT NULL THEN 1 ELSE 0 END) as hasWeight, " +
            "MAX(CASE WHEN reps IS NOT NULL THEN 1 ELSE 0 END) as hasReps, " +
            "MAX(CASE WHEN durationMin IS NOT NULL THEN 1 ELSE 0 END) as hasDuration " +
            "FROM training_entries GROUP BY exerciseId"
    )
    suspend fun exerciseMetrics(): List<ExerciseMetric>
}
