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
import com.seventv.stickers.ui.components.ImportProgressBanner
import com.seventv.stickers.ui.explore.ExploreScreen
import com.seventv.stickers.ui.explore.ExploreViewModel
import com.seventv.stickers.ui.packs.PackDetailScreen
import com.seventv.stickers.ui.packs.PacksScreen
import com.seventv.stickers.ui.packs.PacksViewModel

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

    Scaffold(
        bottomBar = {
            Column {
                ImportProgressBanner(importManager = container.packImportManager)
                if (isBottomBarVisible) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
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
    }
}
