package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    val currentDir by viewModel.currentDir.collectAsState()
    val filesList by viewModel.filesList.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val clipboardFiles by viewModel.clipboardFiles.collectAsState()
    val isCopyAction by viewModel.isCopyAction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeDialogFile by remember { mutableStateOf<FileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileModel?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<List<FileModel>?>(null) }

    val isSelectionMode = selectedFiles.isNotEmpty()

    // Handle android system back key to navigate folders
    BackHandler {
        val traversed = viewModel.navigateUp()
        if (!traversed) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            val traversed = viewModel.navigateUp()
                            if (!traversed) {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = if (isSelectionMode) "تم تحديد ${selectedFiles.size} عناصر" else "متصفح الملفات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
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
                            IconButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "مجلد جديد")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Breadcrumbs navigation
                PathBreadcrumbs(
                    currentDir = currentDir,
                    onPathClick = { file -> viewModel.loadDirectory(file) }
                )

                // Search inside folder
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "ابحث في المجلد الحالي...",
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
            // Filter files list based on search query
            val finalFilesList = remember(filesList, searchQuery) {
                if (searchQuery.isBlank()) {
                    filesList
                } else {
                    filesList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (finalFilesList.isEmpty()) {
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
                    items(finalFilesList, key = { it.path }) { model ->
                        FileListItem(
                            fileModel = model,
                            isSelected = selectedFiles.contains(model),
                            isSelectionMode = isSelectionMode,
                            onItemClick = {
                                if (model.isDirectory) {
                                    viewModel.loadDirectory(File(model.path))
                                } else {
                                    // Open file with system trigger or custom action
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

            // Bottom Floating Paste bar
            AnimatedVisibility(
                visible = clipboardFiles.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val actionTxt = if (isCopyAction) "نسخ" else "نقل"
                            Text(
                                text = "$actionTxt في هذا المجلد",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "عدد العناصر في الحافظة: ${clipboardFiles.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.clearClipboard() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(text = "إلغاء", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.execPaste() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "لصق")
                            }
                        }
                    }
                }
            }
        }
    }

    // Options menu dialog
    activeDialogFile?.let { model ->
        FileOptionsDialog(
            fileModel = model,
            onDismiss = { activeDialogFile = null },
            onRename = { showRenameDialog = model },
            onDelete = { showDeleteConfirmDialog = listOf(model) },
            onCopy = { viewModel.toggleSelection(model); viewModel.setClipboard(true) },
            onCut = { viewModel.toggleSelection(model); viewModel.setClipboard(false) },
            onFavoriteToggle = { viewModel.toggleFavorite(model) },
            onShare = { /* Share action */ }
        )
    }

    // Dialog for renaming files/folders
    showRenameDialog?.let { model ->
        TextInputDialog(
            title = "إعادة التسمية",
            hint = "الاسم الجديد...",
            initialText = model.name,
            confirmLabel = "حفظ",
            onConfirm = { newName ->
                viewModel.renameItem(model, newName)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    // Dialog for creating folders
    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "إنشاء مجلد جديد",
            hint = "اسم المجلد...",
            confirmLabel = "إنشاء",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // Delete confirmation dialog
    showDeleteConfirmDialog?.let { list ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(text = "حذف العناصر", fontWeight = FontWeight.Bold) },
            text = { Text(text = "هل أنت متأكد من حذف ${list.size} عنصر نهائياً؟ لا يمكن استرجاع هذه العناصر لاحقاً.") },
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
