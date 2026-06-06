package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

@Composable
fun DashboardScreen(
    viewModel: FileManagerViewModel,
    onNavigateToExplorer: () -> Unit
) {
    val totalStorage by viewModel.totalStorage.collectAsState()
    val usedStorage by viewModel.usedStorage.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeDialogFile by remember { mutableStateOf<FileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileModel?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<List<FileModel>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageInfo()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome and App Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "أهلاً بك",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "مدير الملفات الذكي",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Search Bar Block
        item {
            CustomSearchBar(
                query = searchQuery,
                onQueryChange = {
                    viewModel.updateSearchQuery(it)
                },
                placeholder = "ابحث عن ملفات وتصنيفات..."
            )
        }

        // If search query is not empty, display search results directly
        if (searchQuery.isNotEmpty()) {
            item {
                Text(
                     text = "نتائج البحث عن: \"$searchQuery\"",
                     fontSize = 16.sp,
                     fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.primary
                )
            }

            // Simple search categories and shortcut list
            // Here we look at favorites or mock list matching search to perform action
            // Or render categorized matches
            val filteredFavorites = favorites.filter { File(it).name.contains(searchQuery, ignoreCase = true) }
            if (filteredFavorites.isEmpty()) {
                item {
                    EmptyPlaceholder(message = "لا توجد نتائج بحث مطابقة", icon = Icons.Default.SearchOff)
                }
            } else {
                items(filteredFavorites) { path ->
                    val file = File(path)
                    val model = viewModel.toFileModel(file)
                    FileListItem(
                        fileModel = model,
                        isSelected = false,
                        isSelectionMode = false,
                        onItemClick = {
                            if (model.isDirectory) {
                                viewModel.loadDirectory(file)
                                viewModel.setScreen(ScreenType.EXPLORER)
                                onNavigateToExplorer()
                            } else {
                                // Launch File
                            }
                        },
                        onItemLongClick = { activeDialogFile = model },
                        onToggleSelect = {},
                        onToggleFavorite = { viewModel.toggleFavorite(model) },
                        viewModel = viewModel
                    )
                }
            }
        } else {
            // Standard Dashboard layout: Storage Progress, Quick category buttons, Favorites, Actions

            // Storage Details
            item {
                StorageProgressCard(
                    used = usedStorage,
                    total = totalStorage,
                    viewModel = viewModel
                )
            }

            // Categories Section Title
            item {
                Text(
                    text = "التصنيفات السريعة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Categories Grid Row (Custom column styling using a custom FlowRow or simple Rows)
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_images),
                                icon = Icons.Default.Image,
                                color = Color(0xFF001D36),
                                count = "تصفح الصور",
                                onClick = { viewModel.selectCategory("images") },
                                containerColor = Color(0xFFD3E4FF),
                                contentColor = Color(0xFF001D36)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_videos),
                                icon = Icons.Default.Movie,
                                color = Color(0xFF251431),
                                count = "تصفح الفيديو",
                                onClick = { viewModel.selectCategory("videos") },
                                containerColor = Color(0xFFF2DFFF),
                                contentColor = Color(0xFF251431)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_audio),
                                icon = Icons.Default.MusicNote,
                                color = Color(0xFF350D00),
                                count = "تصفح الموسيقى",
                                onClick = { viewModel.selectCategory("audio") },
                                containerColor = Color(0xFFFFDBCF),
                                contentColor = Color(0xFF350D00)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_docs),
                                icon = Icons.Default.Description,
                                color = Color(0xFF00210B),
                                count = "تصفح الملفات",
                                onClick = { viewModel.selectCategory("docs") },
                                containerColor = Color(0xFFD1FADF),
                                contentColor = Color(0xFF00210B)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_apks),
                                icon = Icons.Default.Android,
                                color = Color(0xFF33691E),
                                count = "تطبيقات APK",
                                onClick = { viewModel.selectCategory("apks") },
                                containerColor = Color(0xFFF1F8E9),
                                contentColor = Color(0xFF33691E)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryItem(
                                title = stringResource(id = R.string.category_downloads),
                                icon = Icons.Default.FileDownload,
                                color = Color(0xFFE65100),
                                count = "الملفات المحملة",
                                onClick = { viewModel.selectCategory("downloads") },
                                containerColor = Color(0xFFFFF3E0),
                                contentColor = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            // Quick Folder Access Button
            item {
                Button(
                    onClick = {
                        viewModel.setScreen(ScreenType.EXPLORER)
                        onNavigateToExplorer()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "استعراض المجلدات للتخزين الداخلي",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Favorites section title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المفضلة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (favorites.isNotEmpty()) {
                        Text(
                            text = "${favorites.size} عناصر",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Favorites List
            if (favorites.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        message = "لا توجد ملفات تفضيلية بعد.\nاضغط على أيقونة النجمة بجانب أي ملف لإضافته هنا.",
                        icon = Icons.Default.StarOutline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(favorites.toList()) { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val model = viewModel.toFileModel(file)
                        FileListItem(
                            fileModel = model,
                            isSelected = false,
                            isSelectionMode = false,
                            onItemClick = {
                                if (model.isDirectory) {
                                    viewModel.loadDirectory(file)
                                    viewModel.setScreen(ScreenType.EXPLORER)
                                    onNavigateToExplorer()
                                } else {
                                    // Handle open file
                                }
                            },
                            onItemLongClick = { activeDialogFile = model },
                            onToggleSelect = {},
                            onToggleFavorite = { viewModel.toggleFavorite(model) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs for option actions
    activeDialogFile?.let { model ->
        FileOptionsDialog(
            fileModel = model,
            onDismiss = { activeDialogFile = null },
            onRename = { showRenameDialog = model },
            onDelete = { showDeleteConfirmDialog = listOf(model) },
            onCopy = { viewModel.toggleSelection(model); viewModel.setClipboard(true) },
            onCut = { viewModel.toggleSelection(model); viewModel.setClipboard(false) },
            onFavoriteToggle = { viewModel.toggleFavorite(model) },
            onShare = { /* Share functionality trigger */ }
        )
    }

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

    showDeleteConfirmDialog?.let { list ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(text = "حذف الملفات", fontWeight = FontWeight.Bold) },
            text = { Text(text = "هل أنت متأكد من رغبتك في حذف ${list.size} عنصر؟ لا يمكن التراجع عن هذا الإجراء.") },
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
