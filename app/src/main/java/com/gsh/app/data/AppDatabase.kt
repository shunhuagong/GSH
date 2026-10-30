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
        Exercise::class, TrainingEntry::class
    ],
    version = 9,
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
    abstract fun trainingEntryDao(): TrainingEntryDao

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

        /**
         * v6 → v7：训练记录改为逐条模式（对齐饮食记录交互）——
         * 新增 training_entries 表；旧 workouts/workout_sets 表保留在库中但不再使用。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `training_entries` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, `weightKg` REAL, `reps` INTEGER, " +
                        "`durationMin` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_entries_date` ON `training_entries` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_entries_exerciseId` ON `training_entries` (`exerciseId`)")
            }
        }

        /**
         * v7 → v8：exercises 表加 recordType 列（动作记录方式：0=重量×次数，
         * 1=纯次数，2=计时时长，-1=未设置待推断）。单条加列，保持所有数据。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `recordType` INTEGER NOT NULL DEFAULT -1")
            }
        }

        /**
         * v8 → v9：时长统一改用秒——training_entries.durationMin 改名 durationSec，
         * 存量分钟值×60 换算。先改名、后乘 60，两条语句之外不加任何改动；
         * 不涉及饮食表。（RENAME COLUMN 需 SQLite 3.25+/较新系统，用户设备满足。）
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_entries` RENAME COLUMN `durationMin` TO `durationSec`")
                db.execSQL("UPDATE `training_entries` SET `durationSec` = `durationSec` * 60 WHERE `durationSec` IS NOT NULL")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gsh.db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                    )
                    // 手机里已有真实数据：迁移缺失/失败必须抛错暴露，绝不静默清库重建
                    .build()
                    .also { instance = it }
            }
    }
}
