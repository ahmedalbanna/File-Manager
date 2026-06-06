package com.example

import java.io.File

data class FileModel(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val formattedSize: String,
    val lastModified: Long,
    val extension: String,
    val isFavorite: Boolean = false
) {
    val isImage: Boolean
        get() = extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val isVideo: Boolean
        get() = extension.lowercase() in setOf("mp4", "mkv", "avi", "3gp")
    val isAudio: Boolean
        get() = extension.lowercase() in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    val isDoc: Boolean
        get() = extension.lowercase() in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub")
    val isApk: Boolean
        get() = extension.lowercase() == "apk"
}
