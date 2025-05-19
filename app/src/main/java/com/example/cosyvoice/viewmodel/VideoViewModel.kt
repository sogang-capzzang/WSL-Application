package com.example.cosyvoice.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosyvoice.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {
    val videos = mutableStateListOf<Video>()
    val selectedCategory = mutableStateOf("meal") // Default for LipSyncScreen
    val searchQuery = mutableStateOf("")

    fun loadVideos(context: Context, person: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("VideoViewModel", "Loading videos for person: $person")
            val assetManager = context.assets
            val categories = listOf("gymnastics", "exercise", "meal", "medication")

            val allVideos = mutableListOf<Video>()
            categories.forEach { category ->
                try {
                    val files = assetManager.list("$category/$person")?.filter {
                        it.endsWith(".mp4", ignoreCase = true) || it.endsWith(".avi", ignoreCase = true)
                    } ?: emptyList()
                    Log.d("VideoViewModel", "Category: $category, Found ${files.size} files for $person: $files")

                    val categoryVideos = files.map { file ->
                        val path = "$category/$person/$file"
                        Video(
                            id = file,
                            title = file.replace(".mp4", "").replace(".avi", ""),
                            path = path,
                            category = category
                        )
                    }
                    allVideos.addAll(categoryVideos)
                } catch (e: Exception) {
                    Log.e("VideoViewModel", "Error loading files for category $category and person $person: ${e.message}")
                }
            }

            Log.d("VideoViewModel", "Total videos loaded: ${allVideos.size}, Paths: ${allVideos.map { it.path }}")
            videos.clear()
            videos.addAll(allVideos)

            if (allVideos.isEmpty()) {
                Log.w("VideoViewModel", "No videos found for person: $person. Check assets folder structure.")
            }
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
        val filtered = videos.filter { video ->
            video.category == selectedCategory.value && video.title.lowercase().contains(query)
        }
        Log.d("VideoViewModel", "Filtered videos: ${filtered.size}, Category: ${selectedCategory.value}, Query: $query, Paths: ${filtered.map { it.path }}")
        return filtered
    }
}