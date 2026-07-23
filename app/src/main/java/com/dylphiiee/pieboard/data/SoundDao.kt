package com.dylphiiee.pieboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {

    @Query("SELECT * FROM sounds ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<SoundEntity>

    @Query("SELECT * FROM sounds WHERE isFavorite = 1 ORDER BY createdAt ASC")
    suspend fun getFavoritesOnce(): List<SoundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: SoundEntity): Long

    @Update
    suspend fun update(sound: SoundEntity)

    @Delete
    suspend fun delete(sound: SoundEntity)

    @Query("SELECT * FROM sounds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SoundEntity?
}
