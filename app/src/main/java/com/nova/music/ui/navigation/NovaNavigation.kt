package com.nova.music.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.music.ui.screens.home.HomeScreen
import com.nova.music.ui.screens.library.LibraryScreen
import com.nova.music.ui.screens.library.PlaylistDetailScreen
import com.nova.music.ui.screens.search.SearchScreen
import com.nova.music.ui.screens.player.PlayerScreen
import com.nova.music.ui.components.MiniPlayerBar
import com.nova.music.ui.viewmodels.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Player : Screen("player/{songId}") {
        fun createRoute(songId: String) = "player/$songId"
    }
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
}

@Composable
fun NovaNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    val playerViewModel: PlayerViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlayer = { songId ->
                    playerViewModel.loadSong(songId)
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onSongClick = { songId ->
                    playerViewModel.loadSong(songId)
                },
                navController = navController
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate("playlist/$playlistId")
                },
                onNavigateToPlayer = { songId ->
                    playerViewModel.loadSong(songId)
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            PlayerScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { songId ->
                    playerViewModel.loadSong(songId)
                }
            )
        }
    }
}

@Composable
fun NovaNavigation() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Library
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        NovaNavigation(navController)

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Only show mini player and bottom nav if not on PlayerScreen
        val isPlayerScreen = currentRoute?.startsWith("player/") == true

        // Get current song from PlayerViewModel
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val currentSong by playerViewModel.currentSong.collectAsState()

        val navBarHeight = 80.dp
        val miniPlayerGap = 8.dp

        // Mini player hovers above nav bar
        if (currentSong != null && !isPlayerScreen) {
            MiniPlayerBar(
                onTap = {
                    currentSong?.id?.let { songId ->
                        navController.navigate(Screen.Player.createRoute(songId))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = navBarHeight + miniPlayerGap)
                    .fillMaxWidth()
                    .height(navBarHeight)
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        // Navigation bar at the very bottom
        if (!isPlayerScreen) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // No horizontal padding, stretch edge-to-edge
                    .fillMaxWidth()
                    .height(navBarHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E1E)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = when (currentRoute) {
                        "player/${navBackStackEntry?.arguments?.getString("songId")}" -> item.route == Screen.Home.route
                        "playlist/${navBackStackEntry?.arguments?.getString("playlistId")}" -> item.route == Screen.Library.route
                        else -> currentRoute == item.route
                    }
                    val iconSize by animateDpAsState(
                        targetValue = if (selected) 24.dp else 20.dp,
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing
                        )
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier.size(iconSize),
                            tint = if (selected)
                                Color(0xFFBB86FC)
                            else
                                Color(0xFFCCCCDF).copy(alpha = 0.5f)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(
                                animationSpec = tween(200)
                            ) + expandVertically(
                                animationSpec = tween(200),
                                expandFrom = Alignment.Top
                            ),
                            exit = fadeOut(
                                animationSpec = tween(200)
                            ) + shrinkVertically(
                                animationSpec = tween(200),
                                shrinkTowards = Alignment.Top
                            )
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected)
                                    Color(0xFFBB86FC)
                                else
                                    Color(0xFFCCCCDF).copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
} 