package com.gsh.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM foods ORDER BY isCustom DESC, name")
    fun observeAll(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' ORDER BY isCustom DESC, name")
    fun search(query: String): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): Food?

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT name FROM foods")
    suspend fun allNames(): List<String>

    @Query("SELECT * FROM foods WHERE name = :name")
    suspend fun byName(name: String): Food?

    /** 删除指定名称的预置条目（不影响自定义食物）。 */
    @Query("DELETE FROM foods WHERE isCustom = 0 AND name IN (:names)")
    suspend fun deleteNonCustom(names: List<String>)

    @Insert
    suspend fun insert(food: Food): Long

    @Insert
    suspend fun insertAll(foods: List<Food>)

    @Update
    suspend fun update(food: Food)

    @Query("DELETE FROM foods WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: Long)
}
