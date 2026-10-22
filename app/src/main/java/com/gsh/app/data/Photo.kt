package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 打卡照片，分两类：
 * - 餐次照片（KIND_MEAL）：挂在某天的某个餐次上，随打卡展示；
 * - 体型照片（KIND_BODY）：按日期进「变化」页时间线。
 * path 存相对应用私有目录（filesDir）的路径，图片文件由仓库层负责写入与清理。
 */
@Entity(tableName = "photos", indices = [Index("date")])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,       // KIND_MEAL / KIND_BODY
    val date: String,    // yyyy-MM-dd
    val mealType: Int,   // 餐次（MealEntry.MEAL_*）；体型照片无餐次用 MEAL_NONE
    val path: String,
    val createdAt: Long
) {
    companion object {
        const val KIND_MEAL = 0
        const val KIND_BODY = 1
        const val MEAL_NONE = -1
    }
}

@Dao
interface PhotoDao {

    /** 某天的全部餐次照片，按插入顺序 */
    @Query("SELECT * FROM photos WHERE kind = 0 AND date = :date ORDER BY id")
    fun observeMealPhotos(date: String): Flow<List<Photo>>

    /** 全部体型照片，新的日期在前，同一天按先后 */
    @Query("SELECT * FROM photos WHERE kind = 1 ORDER BY date DESC, id ASC")
    fun observeBodyPhotos(): Flow<List<Photo>>

    @Insert
    suspend fun insert(photo: Photo): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}
