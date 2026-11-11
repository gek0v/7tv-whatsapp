package com.seventv.stickers.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seventv.stickers.di.AppContainer
import com.seventv.stickers.ui.channel.ChannelScreen
import com.seventv.stickers.ui.channel.ChannelViewModel
import com.seventv.stickers.ui.components.EmotePeekHost
import com.seventv.stickers.ui.components.ImportProgressBanner
import com.seventv.stickers.ui.components.rememberEmotePeekState
import com.seventv.stickers.ui.explore.ExploreScreen
import com.seventv.stickers.ui.explore.ExploreViewModel
import com.seventv.stickers.ui.packs.PackDetailScreen
import com.seventv.stickers.ui.packs.PacksScreen
import com.seventv.stickers.ui.packs.PacksViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.seventv.stickers.ui.components.UpdateChangelogDialog
import com.seventv.stickers.ui.components.UpdateSettingsDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seventv.stickers.ui.theme.DarkOnSurfaceVariant
import com.seventv.stickers.ui.theme.ElectricCyan
import com.seventv.stickers.ui.theme.GlassSurfaceBar
import com.seventv.stickers.ui.theme.NeonFuchsia

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Explore : Screen("explore", "Explorar", Icons.Default.Search)
    data object Channel : Screen("channel", "Canales", Icons.Default.Tv)
    data object Packs : Screen("packs", "Mis Packs", Icons.Default.Folder)
    data object PackDetail : Screen("pack_detail/{packId}", "Detalle", Icons.Default.Folder) {
        fun createRoute(packId: String): String = "pack_detail/$packId"
    }
}

val bottomNavItems = listOf(
    Screen.Explore,
    Screen.Channel,
    Screen.Packs
)

@Composable
fun AppNavigation(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomBarVisible = bottomNavItems.any { it.route == currentRoute }
    val peekState = rememberEmotePeekState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        container.appUpdateManager.checkForUpdatesIfNeeded()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                container.appUpdateManager.onResumeCheckInstallPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    EmotePeekHost(state = peekState, modifier = modifier) {
    Scaffold(
        bottomBar = {
            Column {
                ImportProgressBanner(importManager = container.packImportManager)
                if (isBottomBarVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = GlassSurfaceBar,
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        ElectricCyan.copy(alpha = 0.55f),
                                        NeonFuchsia.copy(alpha = 0.55f)
                                    )
                                )
                            ),
                            shadowElevation = 14.dp,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                modifier = Modifier.height(68.dp)
                            ) {
                                bottomNavItems.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = screen.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        selected = isSelected,
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color(0x35FF71CE),
                                            selectedIconColor = ElectricCyan,
                                            selectedTextColor = NeonFuchsia,
                                            unselectedIconColor = DarkOnSurfaceVariant.copy(alpha = 0.75f),
                                            unselectedTextColor = DarkOnSurfaceVariant.copy(alpha = 0.75f)
                                        ),
                                        onClick = {
                                            if (currentRoute != screen.route) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Explore.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Explore.route) {
                val exploreViewModel: ExploreViewModel = viewModel(
                    factory = ExploreViewModel.Factory(
                        sevenTvRepository = container.sevenTvRepository,
                        stickerPackRepository = container.stickerPackRepository
                    )
                )
                ExploreScreen(
                    viewModel = exploreViewModel,
                    updateManager = container.appUpdateManager,
                    onNavigateToPacks = {
                        navController.navigate(Screen.Packs.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Channel.route) {
                val channelViewModel: ChannelViewModel = viewModel(
                    factory = ChannelViewModel.Factory(
                        sevenTvRepository = container.sevenTvRepository,
                        stickerPackRepository = container.stickerPackRepository,
                        packImportManager = container.packImportManager
                    )
                )
                ChannelScreen(
                    viewModel = channelViewModel,
                    onNavigateToPacks = {
                        navController.navigate(Screen.Packs.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Packs.route) {
                val packsViewModel: PacksViewModel = viewModel(
                    factory = PacksViewModel.Factory(
                        stickerPackRepository = container.stickerPackRepository,
                        sevenTvRepository = container.sevenTvRepository
                    )
                )
                PacksScreen(
                    viewModel = packsViewModel,
                    updateManager = container.appUpdateManager,
                    onNavigateToPackDetail = { packId ->
                        navController.navigate(Screen.PackDetail.createRoute(packId))
                    },
                    onCreatePackClick = {
                        navController.navigate(Screen.Explore.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.PackDetail.route,
                arguments = listOf(
                    navArgument("packId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val packId = backStackEntry.arguments?.getString("packId") ?: ""
                val packsViewModel: PacksViewModel = viewModel(
                    factory = PacksViewModel.Factory(
                        stickerPackRepository = container.stickerPackRepository,
                        sevenTvRepository = container.sevenTvRepository
                    )
                )
                PackDetailScreen(
                    packId = packId,
                    viewModel = packsViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        UpdateChangelogDialog(updateManager = container.appUpdateManager)
        UpdateSettingsDialog(updateManager = container.appUpdateManager)
    }
    }
}
