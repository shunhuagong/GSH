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

/** 一次训练：当天一场（含时长），组记录见 WorkoutSet。 */
@Entity(tableName = "workouts", indices = [Index("date")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val durationMin: Int,
    val createdAt: Long
)

/** 组记录：某次训练里某个动作的一组（重量 × 次数）。 */
@Entity(
    tableName = "workout_sets",
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int
)

/** 「我」页最近一次训练摘要（一次查询带出动作数与组数）。 */
data class WorkoutSummary(
    val id: Long,
    val date: String,
    val durationMin: Int,
    val exerciseCount: Int,
    val setCount: Int
)

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
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: Workout): Long

    @Query("SELECT * FROM workouts ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<Workout?>

    /** 最近一次包含指定动作的训练（用于"上次成绩"带过）。 */
    @Query(
        "SELECT * FROM workouts WHERE id IN " +
            "(SELECT workoutId FROM workout_sets WHERE exerciseId = :exerciseId) " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun lastWithExercise(exerciseId: Long): Workout?

    @Query(
        "SELECT w.id, w.date, w.durationMin, " +
            "COUNT(DISTINCT s.exerciseId) as exerciseCount, COUNT(s.id) as setCount " +
            "FROM workouts w LEFT JOIN workout_sets s ON s.workoutId = w.id " +
            "GROUP BY w.id ORDER BY w.id DESC LIMIT 1"
    )
    fun observeLatestSummary(): Flow<WorkoutSummary?>
}

@Dao
interface WorkoutSetDao {

    @Insert
    suspend fun insertAll(sets: List<WorkoutSet>)

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId AND exerciseId = :exerciseId ORDER BY id")
    suspend fun ofExerciseInWorkout(workoutId: Long, exerciseId: Long): List<WorkoutSet>
}
