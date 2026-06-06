package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Force Right-to-Left (RTL) Layout Direction specifically for absolute Arabic Design satisfaction
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val viewModel: FileManagerViewModel = viewModel()
                    val context = LocalContext.current

                    var permissionGranted by remember { mutableStateOf(hasStoragePermission(context)) }

                    if (!permissionGranted) {
                        PermissionsScreen(
                            onPermissionGranted = {
                                permissionGranted = true
                                viewModel.refreshStorageInfo()
                            }
                        )
                    } else {
                        MainScreenContainer(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreenContainer(
    viewModel: FileManagerViewModel
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val clipboardFiles by viewModel.clipboardFiles.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show status messages as custom Toast or beautiful Material Snackbar
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        bottomBar = {
            // Only display NavigationBar when not managing selected item states/multi-selections
            AnimatedVisibility(
                visible = selectedFiles.isEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = Color(0xFFF2F5EB),
                    tonalElevation = 8.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    NavigationBarItem(
                        selected = currentScreen == ScreenType.DASHBOARD,
                        onClick = { viewModel.setScreen(ScreenType.DASHBOARD) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF191D17),
                            selectedTextColor = Color(0xFF191D17),
                            indicatorColor = Color(0xFFD7E8CD),
                            unselectedIconColor = Color(0xFF191D17).copy(alpha = 0.6f),
                            unselectedTextColor = Color(0xFF191D17).copy(alpha = 0.6f)
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "الرئيسية"
                            )
                        },
                        label = {
                            Text(
                                text = "الرئيسية",
                                fontWeight = if (currentScreen == ScreenType.DASHBOARD) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = currentScreen == ScreenType.EXPLORER,
                        onClick = { viewModel.setScreen(ScreenType.EXPLORER) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF191D17),
                            selectedTextColor = Color(0xFF191D17),
                            indicatorColor = Color(0xFFD7E8CD),
                            unselectedIconColor = Color(0xFF191D17).copy(alpha = 0.6f),
                            unselectedTextColor = Color(0xFF191D17).copy(alpha = 0.6f)
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "المتصفح"
                            )
                        },
                        label = {
                            Text(
                                text = "المتصفح",
                                fontWeight = if (currentScreen == ScreenType.EXPLORER) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (clipboardFiles.isNotEmpty()) 80.dp else 12.dp)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                ScreenType.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToExplorer = {
                            viewModel.setScreen(ScreenType.EXPLORER)
                        }
                    )
                }
                ScreenType.EXPLORER -> {
                    ExplorerScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.setScreen(ScreenType.DASHBOARD)
                        }
                    )
                }
                ScreenType.CATEGORY -> {
                    CategoryScreen(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.setScreen(ScreenType.DASHBOARD)
                        }
                    )
                }
                ScreenType.FAVORITES -> {
                    // Handled inside dashboard screen search lists natively
                    viewModel.setScreen(ScreenType.DASHBOARD)
                }
            }
        }
    }
}
