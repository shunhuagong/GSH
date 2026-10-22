package com.gsh.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Food::class, MealEntry::class, DayType::class, Photo::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun dailyTotalDao(): DailyTotalDao
    abstract fun dayTypeDao(): DayTypeDao
    abstract fun photoDao(): PhotoDao

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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gsh.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // 其他未显式迁移的版本变更才走重建（早期开发阶段保护）
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
