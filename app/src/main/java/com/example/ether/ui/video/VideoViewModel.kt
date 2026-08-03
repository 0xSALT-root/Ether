package com.example.ether.ui.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor() : ViewModel() {
    var activeVideoUri by mutableStateOf<String?>(null)
    var isMinimized by mutableStateOf(false)

    fun playVideo(uri: String) {
        activeVideoUri = uri
        isMinimized = false
    }
    
    fun minimize() {
        isMinimized = true
    }
    
    fun closeVideo() {
        activeVideoUri = null
        isMinimized = false
    }
}
