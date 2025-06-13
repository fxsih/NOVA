package com.nova.music.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.nova.music.data.model.Playlist
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist
} 