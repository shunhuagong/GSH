package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 训练动作：内置（用户实际使用的三个）+ 自定义补充。
 * 名称保留用户口径，不擅自改写。
 */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,   // 腿 / 背 / 胸 / 肩 / 臂 / 核心
    val isCustom: Boolean = false
)

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
}

@Dao
interface TrainingEntryDao {

    @Query("SELECT * FROM training_entries WHERE date = :date ORDER BY id")
    fun observeByDate(date: String): Flow<List<TrainingEntry>>

    @Query("SELECT * FROM training_entries WHERE exerciseId = :exerciseId ORDER BY id DESC LIMIT 1")
    suspend fun lastOf(exerciseId: Long): TrainingEntry?

    @Insert
    suspend fun insert(entry: TrainingEntry): Long

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
}
