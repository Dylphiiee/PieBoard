package com.dylphiiee.pieboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sounds")
data class SoundEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
