package com.nova.music.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nova.music.ui.screens.auth.LoginScreen
import com.nova.music.ui.screens.auth.SignupScreen
import com.nova.music.ui.screens.profile.ProfileScreen
import com.nova.music.ui.components.MiniPlayerBar
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.AuthViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Profile : Screen("profile")
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Player : Screen("player/{songId}?") {
        fun createRoute(songId: String? = null) = songId?.let { "player/$it" } ?: "player"
    }
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
}

@Composable
fun NovaNavigation(
    navController: NavHostController,
    startDestination: String,
    isAuthenticated: Boolean
) {
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication screens
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    android.util.Log.d("NovaNavigation", "Login successful, navigating to Home screen")
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    android.util.Log.d("NovaNavigation", "Navigating to Sign Up screen")
                    navController.navigate(Screen.Signup.route)
                },
                onTestPlayer = {
                    // Only for development
                    android.util.Log.d("NovaNavigation", "Using test player mode")
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Signup.route) {
            SignupScreen(
                onNavigateBack = {
                    android.util.Log.d("NovaNavigation", "Navigating back from Sign Up screen")
                    navController.popBackStack()
                },
                onSignUpSuccess = {
                    android.util.Log.d("NovaNavigation", "Sign Up successful, navigating to Login screen")
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Signup.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Main app screens
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlayer = { songId ->
                    // Navigation to player is now handled by LaunchedEffect in NovaNavigation()
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onSongClick = { songId ->
                    // Directly load the song in PlayerViewModel instead of just navigating
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
                    // Navigation to player is now handled by LaunchedEffect in NovaNavigation()
                }
            )
        }

        composable(
            route = "player/{songId}",
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it }, // Start from bottom
                    animationSpec = tween(300, easing = EaseOutQuart)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it }, // Exit to bottom
                    animationSpec = tween(300, easing = EaseInQuart)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            PlayerScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "player",
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it }, // Start from bottom
                    animationSpec = tween(300, easing = EaseOutQuart)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it }, // Exit to bottom
                    animationSpec = tween(300, easing = EaseInQuart)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            PlayerScreen(
                songId = null,
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
                    // The playlist ID will be set by the PlaylistDetailScreen component
                    // Navigation to player is now handled by LaunchedEffect in NovaNavigation()
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
    
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    
    val shouldShowFullPlayer by playerViewModel.shouldShowFullPlayer.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isInPlayerScreen by playerViewModel.isInPlayerScreen.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isAuthenticated = currentUser != null
    
    val startDestination = if (isAuthenticated) Screen.Home.route else Screen.Login.route
    val coroutineScope = rememberCoroutineScope()
    
    // Handle automatic navigation to full player based on shouldShowFullPlayer flag
    LaunchedEffect(shouldShowFullPlayer, currentSong) {
        if (shouldShowFullPlayer && currentSong != null && !isInPlayerScreen) {
            navController.navigate(Screen.Player.createRoute())
        }
    }
    
    // Reset full player flag when app starts
    LaunchedEffect(Unit) {
        playerViewModel.resetFullPlayerShownFlag()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        NovaNavigation(
            navController = navController,
            startDestination = startDestination,
            isAuthenticated = isAuthenticated
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Only show mini player and bottom nav if not on PlayerScreen and authenticated
        val isPlayerScreen = currentRoute?.startsWith("player") == true
        val isAuthScreen = currentRoute == Screen.Login.route || currentRoute == Screen.Signup.route
        
        // Update the isInPlayerScreen flag in the ViewModel
        LaunchedEffect(isPlayerScreen) {
            playerViewModel.setInPlayerScreen(isPlayerScreen)
        }

        val navBarHeight = 72.dp
        val miniPlayerGap = 8.dp

        // Mini player hovers above nav bar (only when authenticated and not on auth screens)
        if (currentSong != null && !isPlayerScreen && isAuthenticated && !isAuthScreen) {
            MiniPlayerBar(
                onTap = {
                    // Navigate to player without song ID to prevent reloading
                    navController.navigate(Screen.Player.createRoute())
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = navBarHeight + miniPlayerGap)
                    .fillMaxWidth()
                    .height(navBarHeight)
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        // Navigation bar at the very bottom (only when authenticated and not on auth screens)
        if (!isPlayerScreen && isAuthenticated && !isAuthScreen) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBarHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1E1E)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = when (currentRoute) {
                        "player/${navBackStackEntry?.arguments?.getString("songId")}", "player" -> item.route == Screen.Home.route
                        "playlist/${navBackStackEntry?.arguments?.getString("playlistId")}" -> item.route == Screen.Library.route
                        Screen.Profile.route -> false
                        else -> currentRoute == item.route
                    }
                    
                    // Each nav item has equal width
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Simple column with icon and text
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        ) {
                            // Use a Box with fixed height for the icon to ensure consistent positioning
                            Box(
                                modifier = Modifier
                                    .size(24.dp) // Fixed container size for all icons
                                    .padding(0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Icon with size animation
                                val iconSize by animateDpAsState(
                                    targetValue = if (selected) 23.dp else 19.dp,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(iconSize),
                                    tint = if (selected)
                                        Color(0xFFBB86FC)
                                    else
                                        Color(0xFFCCCCDF).copy(alpha = 0.5f)
                                )
                            }
                            
                            // Fixed height container for text to ensure consistent layout
                            Box(
                                modifier = Modifier
                                    .height(18.dp) // Fixed height for text area
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected)
                                        Color(0xFFBB86FC)
                                    else
                                        Color(0xFFCCCCDF).copy(alpha = 0f), // Invisible but takes space
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
} 