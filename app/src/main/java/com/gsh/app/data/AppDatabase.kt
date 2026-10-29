package com.gsh.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Food::class, MealEntry::class, DayType::class, Photo::class, WeightLog::class,
        Exercise::class, Workout::class, WorkoutSet::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun dailyTotalDao(): DailyTotalDao
    abstract fun dayTypeDao(): DayTypeDao
    abstract fun photoDao(): PhotoDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSetDao(): WorkoutSetDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v2 → v3：新增 day_types 表，保留所有已有数据。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_types` " +
                        "(`date` TEXT NOT NULL, `isTraining` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** v3 → v4：新增 photos 表（餐次/体型打卡照片），保留所有已有数据。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photos` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `mealType` INTEGER NOT NULL, " +
                        "`path` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_date` ON `photos` (`date`)")
            }
        }

        /** v4 → v5：新增 weight_logs 表（体重记录，一天一条），保留所有已有数据。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_logs` " +
                        "(`date` TEXT NOT NULL, `weightKg` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** v5 → v6：新增训练记录三表（动作库 / 训练 / 组记录），保留所有已有数据。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`muscleGroup` TEXT NOT NULL, `isCustom` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workouts` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`durationMin` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_date` ON `workouts` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutId` ON `workout_sets` (`workoutId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_exerciseId` ON `workout_sets` (`exerciseId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gsh.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // 其他未显式迁移的版本变更才走重建（早期开发阶段保护）
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
