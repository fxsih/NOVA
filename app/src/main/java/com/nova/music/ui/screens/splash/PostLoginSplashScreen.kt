package com.nova.music.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.R
import com.nova.music.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.delay

@Composable
fun PostLoginSplashScreen(
    onSetupComplete: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    var currentStep by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    
    // Animation for the loading dots
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val dotsAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots"
    )
    
    // Gradient background
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E1E1E),
            Color(0xFF2D2D2D),
            Color(0xFF1E1E1E)
        )
    )
    
    // Setup steps
    val setupSteps = listOf(
        "Setting up the app...",
        "Loading trending songs...",
        "Preparing your music library...",
        "Almost ready..."
    )
    
    LaunchedEffect(Unit) {
        // Preload trending songs
        homeViewModel.loadTrendingSongs()
        
        // Simulate setup steps
        for (step in setupSteps.indices) {
            currentStep = step
            delay(800) // Show each step for 800ms
        }
        
        // Wait a bit more for actual loading
        delay(500)
        
        // Complete setup
        isLoading = false
        onSetupComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "NOVA Music Logo",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App Name
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Music",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Loading Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Current step text
                    Text(
                        text = setupSteps.getOrNull(currentStep) ?: "Setting up...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Loading indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Color.White.copy(
                                            alpha = if (index == 0) dotsAlpha else 0.3f
                                        )
                                    )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = (currentStep + 1).toFloat() / setupSteps.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Additional info
            Text(
                text = "Your music journey is about to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
} 