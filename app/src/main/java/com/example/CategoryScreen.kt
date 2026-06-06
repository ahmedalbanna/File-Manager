package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    val categoryName by viewModel.currentCategory.collectAsState()
    val categoryFiles by viewModel.categoryFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeDialogFile by remember { mutableStateOf<FileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileModel?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<List<FileModel>?>(null) }

    val isSelectionMode = selectedFiles.isNotEmpty()

    val displayTitle = when (categoryName) {
        "images" -> stringResource(id = R.string.category_images)
        "videos" -> stringResource(id = R.string.category_videos)
        "audio" -> stringResource(id = R.string.category_audio)
        "docs" -> stringResource(id = R.string.category_docs)
        "apks" -> stringResource(id = R.string.category_apks)
        "downloads" -> stringResource(id = R.string.category_downloads)
        else -> "تصنيف"
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    },
                    title = {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isSelectionMode) "تم تحديد ${selectedFiles.size} عناصر" else displayTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel.setClipboard(true) }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "نسخ")
                            }
                            IconButton(onClick = { viewModel.setClipboard(false) }) {
                                Icon(imageVector = Icons.Default.ContentCut, contentDescription = "قص")
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = selectedFiles.toList() }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "إلغاء التحديد")
                            }
                        } else {
                            IconButton(onClick = { viewModel.loadCategoryFiles(categoryName) }) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Search inside category
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث في هذا التصنيف...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val filteredList = remember(categoryFiles, searchQuery) {
                if (searchQuery.isBlank()) {
                    categoryFiles
                } else {
                    categoryFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredList.isEmpty()) {
                val msg = if (searchQuery.isNotBlank()) "لا توجد نتائج بحث مطابقة" else "المجلد فارغ"
                EmptyPlaceholder(
                    message = msg,
                    icon = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.FolderOpen,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredList, key = { it.path }) { model ->
                        FileListItem(
                            fileModel = model,
                            isSelected = selectedFiles.contains(model),
                            isSelectionMode = isSelectionMode,
                            onItemClick = {
                                if (model.isDirectory) {
                                    viewModel.loadDirectory(File(model.path))
                                    viewModel.setScreen(ScreenType.EXPLORER)
                                } else {
                                    // Handle open file
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.toggleSelection(model)
                                } else {
                                    activeDialogFile = model
                                }
                            },
                            onToggleSelect = { viewModel.toggleSelection(model) },
                            onToggleFavorite = { viewModel.toggleFavorite(model) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs
    activeDialogFile?.let { model ->
        FileOptionsDialog(
            fileModel = model,
            onDismiss = { activeDialogFile = null },
            onRename = { showRenameDialog = model },
            onDelete = { showDeleteConfirmDialog = listOf(model) },
            onCopy = { viewModel.toggleSelection(model); viewModel.setClipboard(true) },
            onCut = { viewModel.toggleSelection(model); viewModel.setClipboard(false) },
            onFavoriteToggle = { viewModel.toggleFavorite(model) },
            onShare = { /* Share */ }
        )
    }

    // Rename dialog
    showRenameDialog?.let { model ->
        TextInputDialog(
            title = "إعادة تسمية الملف/المجلد",
            hint = "أدخل الاسم الجديد...",
            initialText = model.name,
            confirmLabel = "حفظ",
            onConfirm = { newName ->
                viewModel.renameItem(model, newName)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    // Delete dialog
    showDeleteConfirmDialog?.let { list ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(text = "حذف العناصر", fontWeight = FontWeight.Bold) },
            text = { Text(text = "هل أنت متأكد من حذف ${list.size} عنصر نهائياً؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItems(list)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(text = "إلغاء")
                }
            }
        )
    }
}
