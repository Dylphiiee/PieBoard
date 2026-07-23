package com.dylphiiee.pieboard.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dylphiiee.pieboard.data.SoundEntity
import com.dylphiiee.pieboard.data.SoundRepository
import com.dylphiiee.pieboard.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SoundRepository(application)
    val prefs = Prefs(application)

    val sounds: StateFlow<List<SoundEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val searchQueryFlow = MutableStateFlow("")
    val searchQuery: StateFlow<String> = searchQueryFlow

    /** [sounds] filtered by the current search query (case-insensitive substring match). */
    val filteredSounds: StateFlow<List<SoundEntity>> =
        combine(sounds, searchQueryFlow) { list, query ->
            if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun addSound(name: String, uri: Uri) {
        viewModelScope.launch { repository.addSound(name, uri) }
    }

    fun updateSound(existing: SoundEntity, newName: String, newUri: Uri?) {
        viewModelScope.launch { repository.updateSound(existing, newName, newUri) }
    }

    fun deleteSound(sound: SoundEntity) {
        viewModelScope.launch { repository.deleteSound(sound) }
    }

    fun toggleFavorite(sound: SoundEntity) {
        viewModelScope.launch { repository.toggleFavorite(sound) }
    }

    fun getSoundById(id: Long, callback: (SoundEntity?) -> Unit) {
        viewModelScope.launch { callback(repository.getById(id)) }
    }
}
