package com.dylphiiee.pieboard.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SoundRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).soundDao()
    private val soundsDir = File(appContext.filesDir, "sounds").apply { if (!exists()) mkdirs() }

    fun observeAll(): Flow<List<SoundEntity>> = dao.observeAll()

    /**
     * Copies the picked audio [uri] into internal storage and inserts a new [SoundEntity].
     */
    suspend fun addSound(name: String, uri: Uri): SoundEntity = withContext(Dispatchers.IO) {
        val destFile = File(soundsDir, "${UUID.randomUUID()}.audio")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        val entity = SoundEntity(name = name, filePath = destFile.absolutePath)
        val id = dao.insert(entity)
        entity.copy(id = id)
    }

    /**
     * Updates name, and optionally replaces the audio file if [newUri] is not null.
     */
    suspend fun updateSound(existing: SoundEntity, newName: String, newUri: Uri?): SoundEntity =
        withContext(Dispatchers.IO) {
            var filePath = existing.filePath
            if (newUri != null) {
                // remove old file, write new one
                File(existing.filePath).let { if (it.exists()) it.delete() }
                val destFile = File(soundsDir, "${UUID.randomUUID()}.audio")
                appContext.contentResolver.openInputStream(newUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                filePath = destFile.absolutePath
            }
            val updated = existing.copy(name = newName, filePath = filePath)
            dao.update(updated)
            updated
        }

    suspend fun deleteSound(sound: SoundEntity) = withContext(Dispatchers.IO) {
        File(sound.filePath).let { if (it.exists()) it.delete() }
        dao.delete(sound)
    }

    suspend fun getAllOnce(): List<SoundEntity> = withContext(Dispatchers.IO) { dao.getAllOnce() }

    suspend fun getFavoritesOnce(): List<SoundEntity> = withContext(Dispatchers.IO) { dao.getFavoritesOnce() }

    suspend fun toggleFavorite(sound: SoundEntity) = withContext(Dispatchers.IO) {
        dao.update(sound.copy(isFavorite = !sound.isFavorite))
    }

    suspend fun getById(id: Long): SoundEntity? = withContext(Dispatchers.IO) { dao.getById(id) }
}
