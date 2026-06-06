package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

enum class ScreenType {
    DASHBOARD,
    EXPLORER,
    CATEGORY,
    FAVORITES
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences(
        "file_manager_prefs", Context.MODE_PRIVATE
    )

    // Screen State
    private val _currentScreen = MutableStateFlow(ScreenType.DASHBOARD)
    val currentScreen: StateFlow<ScreenType> = _currentScreen.asStateFlow()

    // Explorer State
    private val _currentDir = MutableStateFlow<File>(Environment.getExternalStorageDirectory())
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _filesList = MutableStateFlow<List<FileModel>>(emptyList())
    val filesList: StateFlow<List<FileModel>> = _filesList.asStateFlow()

    // Selection State
    private val _selectedFiles = MutableStateFlow<Set<FileModel>>(emptySet())
    val selectedFiles: StateFlow<Set<FileModel>> = _selectedFiles.asStateFlow()

    // Clipboard State
    private val _clipboardFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val clipboardFiles: StateFlow<List<FileModel>> = _clipboardFiles.asStateFlow()

    private val _isCopyAction = MutableStateFlow(true) // true for copy, false for cut/move
    val isCopyAction: StateFlow<Boolean> = _isCopyAction.asStateFlow()

    // Storage State
    private val _totalStorage = MutableStateFlow(0L)
    val totalStorage: StateFlow<Long> = _totalStorage.asStateFlow()

    private val _usedStorage = MutableStateFlow(0L)
    val usedStorage: StateFlow<Long> = _usedStorage.asStateFlow()

    private val _freeStorage = MutableStateFlow(0L)
    val freeStorage: StateFlow<Long> = _freeStorage.asStateFlow()

    // Category State
    private val _currentCategory = MutableStateFlow("")
    val currentCategory: StateFlow<String> = _currentCategory.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val categoryFiles: StateFlow<List<FileModel>> = _categoryFiles.asStateFlow()

    // Favorites State
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Loading & Status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Load initial state
        loadFavorites()
        refreshStorageInfo()
        loadDirectory(_currentDir.value)
    }

    fun setScreen(screen: ScreenType) {
        _currentScreen.value = screen
        _selectedFiles.value = emptySet()
        if (screen == ScreenType.DASHBOARD) {
            refreshStorageInfo()
        }
    }

    fun selectCategory(category: String) {
        _currentCategory.value = category
        _currentScreen.value = ScreenType.CATEGORY
        loadCategoryFiles(category)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stat = Environment.getExternalStorageDirectory()
                val total = stat.totalSpace
                val free = stat.freeSpace
                val used = total - free
                _totalStorage.value = total
                _freeStorage.value = free
                _usedStorage.value = used
            } catch (e: Exception) {
                Log.e("FileManagerVM", "Error reading storage usage info", e)
            }
        }
    }

    fun loadDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) {
            _statusMessage.value = "المسار غير صالح أو لا يمكن الوصول إليه"
            return
        }
        _currentDir.value = directory
        _selectedFiles.value = emptySet()
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = directory.listFiles()
                val models = if (files != null) {
                    files.map { toFileModel(it) }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } else {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    _filesList.value = models
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("FileManagerVM", "Error listing files in: ${directory.path}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "فشل تحميل محتويات المجلد"
                    _isLoading.value = false
                }
            }
        }
    }

    fun navigateUp(): Boolean {
        if (_currentScreen.value == ScreenType.EXPLORER) {
            val parent = _currentDir.value.parentFile
            val externalRoot = Environment.getExternalStorageDirectory()
            if (parent != null && _currentDir.value != externalRoot && _currentDir.value.path != "/") {
                loadDirectory(parent)
                return true
            } else {
                setScreen(ScreenType.DASHBOARD)
                return true
            }
        } else if (_currentScreen.value != ScreenType.DASHBOARD) {
            setScreen(ScreenType.DASHBOARD)
            return true
        }
        return false // Can exit app or pass to system
    }

    // Toggle multi-selection files
    fun toggleSelection(fileModel: FileModel) {
        val currentSet = _selectedFiles.value.toMutableSet()
        if (currentSet.contains(fileModel)) {
            currentSet.remove(fileModel)
        } else {
            currentSet.add(fileModel)
        }
        _selectedFiles.value = currentSet
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    // Clipboard Copy & Cut actions
    fun setClipboard(isCopy: Boolean) {
        _clipboardFiles.value = _selectedFiles.value.toList()
        _isCopyAction.value = isCopy
        _selectedFiles.value = emptySet()
        _statusMessage.value = if (isCopy) "تم نسخ العناصر للحافظة" else "تم نقل العناصر للحافظة (جاهز للصق)"
    }

    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
    }

    // Core File actions
    fun createFolder(name: String) {
        val newFolder = File(_currentDir.value, name)
        if (newFolder.exists()) {
            _statusMessage.value = "المجلد موجود بالفعل"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val created = newFolder.mkdir()
                withContext(Dispatchers.Main) {
                    if (created) {
                        _statusMessage.value = "تم إنشاء المجلد بنجاح"
                        loadDirectory(_currentDir.value)
                    } else {
                        _statusMessage.value = "فشل إنشاء المجلد"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "خطأ في إنشاء المجلد: ${e.message}"
                }
            }
        }
    }

    fun renameItem(fileModel: FileModel, newName: String) {
        val sourceFile = File(fileModel.path)
        val ext = if (fileModel.isDirectory) "" else "." + sourceFile.extension
        val cleanNewName = if (fileModel.isDirectory || newName.endsWith(ext)) newName else "$newName$ext"
        val destFile = File(sourceFile.parentFile, cleanNewName)

        if (destFile.exists()) {
            _statusMessage.value = "يوجد ملف أو مجلد بالاسم الجديد بالفعل"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val renamed = sourceFile.renameTo(destFile)
                withContext(Dispatchers.Main) {
                    if (renamed) {
                        _statusMessage.value = "تمت إعادة التسمية بنجاح"
                        // Refresh based on screen type
                        if (_currentScreen.value == ScreenType.EXPLORER) {
                            loadDirectory(_currentDir.value)
                        } else if (_currentScreen.value == ScreenType.CATEGORY) {
                            loadCategoryFiles(_currentCategory.value)
                        } else if (_currentScreen.value == ScreenType.FAVORITES) {
                            // Update favorites path if it was favorited
                            if (_favorites.value.contains(fileModel.path)) {
                                val favs = _favorites.value.toMutableSet()
                                favs.remove(fileModel.path)
                                favs.add(destFile.path)
                                saveFavoritesSet(favs)
                            }
                        }
                    } else {
                        _statusMessage.value = "فشل عملية إعادة التسمية"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "خطأ في إعادة التسمية: ${e.message}"
                }
            }
        }
    }

    fun deleteItems(items: List<FileModel>) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            for (item in items) {
                try {
                    val file = File(item.path)
                    if (deleteRecursively(file)) {
                        deletedCount++
                        if (_favorites.value.contains(item.path)) {
                            removeFavorite(item.path)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FileManagerVM", "Error deleting item: ${item.path}", e)
                }
            }
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _statusMessage.value = "تم حذف $deletedCount عنصر بنجاح"
                _selectedFiles.value = emptySet()
                if (_currentScreen.value == ScreenType.EXPLORER) {
                    loadDirectory(_currentDir.value)
                } else if (_currentScreen.value == ScreenType.CATEGORY) {
                    loadCategoryFiles(_currentCategory.value)
                }
            }
        }
    }

    fun execPaste() {
        val targets = _clipboardFiles.value
        if (targets.isEmpty()) return

        val destDir = _currentDir.value
        val isCopy = _isCopyAction.value
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0

            for (model in targets) {
                val src = File(model.path)
                val dst = File(destDir, src.name)

                if (src.absolutePath == dst.absolutePath) {
                    failCount++
                    continue
                }

                try {
                    val completed = if (isCopy) {
                        copyRecursively(src, dst)
                    } else {
                        // Move operation
                        val moved = src.renameTo(dst)
                        if (moved) true else {
                            // fallback: copy then delete
                            val Copied = copyRecursively(src, dst)
                            if (Copied) {
                                deleteRecursively(src)
                                true
                            } else false
                        }
                    }
                    if (completed) successCount++ else failCount++
                } catch (e: Exception) {
                    Log.e("FileManagerVM", "Error pasting: ${src.path}", e)
                    failCount++
                }
            }

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                _statusMessage.value = if (failCount == 0) {
                    "تم إجراء العملية على $successCount عنصر بنجاح"
                } else {
                    "تمت العملية لـ $successCount وفشل $failCount عنصر"
                }
                if (!isCopy) {
                    _clipboardFiles.value = emptyList() // clear move items after action
                }
                loadDirectory(_currentDir.value)
            }
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursively(child)
                }
            }
        }
        return file.delete()
    }

    private fun copyRecursively(src: File, dst: File): Boolean {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) {
                return false
            }
            val children = src.listFiles() ?: return true
            for (child in children) {
                if (!copyRecursively(child, File(dst, child.name))) {
                    return false
                }
            }
            return true
        } else {
            dst.parentFile?.mkdirs()
            return try {
                FileInputStream(src).use { input ->
                    FileOutputStream(dst).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } catch (e: IOException) {
                Log.e("FileManagerVM", "Failed to copy file ${src.path}", e)
                false
            }
        }
    }

    // Category Loading Logic
    fun loadCategoryFiles(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val list = mutableListOf<File>()
            val roots = when (category) {
                "images" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                )
                "videos" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                )
                "audio" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                )
                "downloads" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                )
                "docs" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                )
                "apks" -> listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )
                else -> emptyList()
            }

            for (root in roots) {
                if (root.exists()) {
                    scanDirForCategory(root, category, list)
                }
            }

            // Map and distinct
            val models = list.distinctBy { it.absolutePath }
                .map { toFileModel(it) }
                .sortedByDescending { it.lastModified }

            withContext(Dispatchers.Main) {
                _categoryFiles.value = models
                _isLoading.value = false
            }
        }
    }

    private fun scanDirForCategory(dir: File, category: String, results: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirForCategory(file, category, results)
            } else {
                val matches = when (category) {
                    "images" -> file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                    "videos" -> file.extension.lowercase() in setOf("mp4", "mkv", "avi", "3gp")
                    "audio" -> file.extension.lowercase() in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
                    "docs" -> file.extension.lowercase() in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub")
                    "apks" -> file.extension.lowercase() == "apk"
                    "downloads" -> true
                    else -> false
                }
                if (matches) {
                    results.add(file)
                }
            }
        }
    }

    // Favorites Logic with SharedPreferences
    private fun loadFavorites() {
        val saved = sharedPrefs.getStringSet("favorites_set", emptySet()) ?: emptySet()
        _favorites.value = saved
    }

    private fun saveFavoritesSet(set: Set<String>) {
        sharedPrefs.edit().putStringSet("favorites_set", set).apply()
        _favorites.value = set
    }

    fun toggleFavorite(fileModel: FileModel) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(fileModel.path)) {
            current.remove(fileModel.path)
            _statusMessage.value = "تمت الإزالة من المفضلة"
        } else {
            current.add(fileModel.path)
            _statusMessage.value = "تمت الإضافة للمفضلة"
        }
        saveFavoritesSet(current)
        // refresh filesList to reflect icon changes if in explorer
        if (_currentScreen.value == ScreenType.EXPLORER) {
            loadDirectory(_currentDir.value)
        }
    }

    private fun removeFavorite(path: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
            saveFavoritesSet(current)
        }
    }

    // Helper conversion
    fun toFileModel(file: File): FileModel {
        val size = if (file.isDirectory) {
            // Count items inside instead of physical folder size block
            file.listFiles()?.size?.toLong() ?: 0L
        } else {
            file.length()
        }
        val formatted = if (file.isDirectory) {
            "عناصر: $size"
        } else {
            formatFileSize(size)
        }
        val isFav = _favorites.value.contains(file.absolutePath)
        return FileModel(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = size,
            formattedSize = formatted,
            lastModified = file.lastModified(),
            extension = file.extension,
            isFavorite = isFav
        )
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 ب"
        val units = arrayOf("ب", "ك.ب", "م.ب", "ج.ب", "ت.ب")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups >= units.size) units.size - 1 else digitGroups
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format("%.2f %s", value, units[index])
    }
}
