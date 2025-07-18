package com.nova.music

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {
    
    companion object {
        private const val SPLASH_DELAY = 1500L // 1.5 seconds
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the content view to the splash layout
        setContentView(R.layout.activity_splash)
        
        // Make the app fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Use a handler to delay the transition to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            // Start the main activity
            startActivity(Intent(this, MainActivity::class.java))
            
            // Close the splash activity
            finish()
            
            // Add custom fade animations for the transition
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }, SPLASH_DELAY)
    }
} 