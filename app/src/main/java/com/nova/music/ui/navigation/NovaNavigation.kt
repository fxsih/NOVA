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
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlayer = { songId ->
                    navController.navigate(Screen.Player.createRoute(songId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onSongClick = { songId ->
                    navController.navigate(Screen.Player.createRoute(songId))
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
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
                    navController.navigate(Screen.Player.createRoute(songId))
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

        // Custom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            Row(
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

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
                                Color(0xFFBB86FC)  // Purple accent
                            else 
                                Color(0xFFCCCCDF).copy(alpha = 0.5f)  // Dimmed white
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
                                    Color(0xFFBB86FC)  // Purple accent
                                else 
                                    Color(0xFFCCCCDF).copy(alpha = 0.5f),  // Dimmed white
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