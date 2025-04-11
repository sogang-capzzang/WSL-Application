package com.example.cosyvoice.model

data class Video(
    val id: String,
    val title: String,
    val path: String, // assets 폴더 내 경로
    val category: String, // "exercise" 또는 "lipsync"
    var isFavorite: Boolean = false
)