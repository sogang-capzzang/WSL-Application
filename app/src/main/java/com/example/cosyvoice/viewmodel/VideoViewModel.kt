package com.example.cosyvoice.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosyvoice.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {
    val videos = mutableStateListOf<Video>()
    val selectedCategory = mutableStateOf("exercise")
    val searchQuery = mutableStateOf("")

    fun loadVideos(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val assetManager = context.assets
            val exerciseFiles = assetManager.list("exercise")?.filter { it.endsWith(".mp4") } ?: emptyList()
            val lipSyncFiles = assetManager.list("lipsync")?.filter { it.endsWith(".mp4") } ?: emptyList()

            val exerciseVideos = exerciseFiles.map { file ->
                Video(
                    id = file,
                    title = file.replace(".mp4", ""),
                    path = "exercise/$file",
                    category = "exercise"
                )
            }
            val lipSyncVideos = lipSyncFiles.map { file ->
                Video(
                    id = file,
                    title = file.replace(".mp4", ""),
                    path = "lipsync/$file",
                    category = "lipsync"
                )
            }

            videos.clear()
            videos.addAll(exerciseVideos + lipSyncVideos)
        }
    }

    fun toggleFavorite(video: Video) {
        val index = videos.indexOf(video)
        if (index != -1) {
            videos[index] = video.copy(isFavorite = !video.isFavorite)
        }
    }

    fun getFilteredVideos(): List<Video> {
        val query = searchQuery.value.lowercase()
        return videos.filter { video ->
            video.category == selectedCategory.value && video.title.lowercase().contains(query)
        }
    }
}