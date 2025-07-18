package com.nova.music.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.ui.viewmodels.AuthViewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.nova.music.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onTestPlayer: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetEmailSent by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Configure Google Sign In
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Activity result launcher for Google Sign In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            
            account?.idToken?.let { idToken ->
                android.util.Log.d("LoginScreen", "Google Sign In successful, id token: ${idToken.take(10)}...")
                viewModel.signInWithGoogle(idToken)
            } ?: run {
                android.util.Log.e("LoginScreen", "Google Sign In failed: ID Token is null")
                error = "Google Sign In failed. Please try again."
            }
        } catch (e: ApiException) {
            // Get detailed error message based on status code
            val errorMessage = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in was cancelled"
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign in failed - check your internet connection"
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Sign in is already in progress"
                GoogleSignInStatusCodes.INVALID_ACCOUNT -> "Invalid account selected"
                GoogleSignInStatusCodes.SIGN_IN_REQUIRED -> "Sign in required"
                GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error occurred"
                GoogleSignInStatusCodes.INTERNAL_ERROR -> "Internal error occurred"
                GoogleSignInStatusCodes.DEVELOPER_ERROR -> "Developer error: Check your Google Sign-In configuration"
                else -> "Google Sign In failed: Error code ${e.statusCode}"
            }
            android.util.Log.e("LoginScreen", "Google Sign In failed with code ${e.statusCode}: ${e.message}", e)
            error = errorMessage
        }
    }

    // Add timeout for loading state
    LaunchedEffect(isLoading) {
        if (isLoading) {
            // If loading takes more than 15 seconds, assume something went wrong
            kotlinx.coroutines.delay(15000)
            if (isLoading) {
                isLoading = false
                error = "Sign in is taking too long. Please try again."
                android.util.Log.e("LoginScreen", "Sign-in timeout occurred")
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.authState.collect { state ->
            android.util.Log.d("LoginScreen", "Auth state changed: $state")
            when (state) {
                is AuthViewModel.AuthState.Success -> {
                    isLoading = false
                    error = null
                    android.util.Log.d("LoginScreen", "Sign-in successful, navigating to home")
                    onLoginSuccess()
                }
                is AuthViewModel.AuthState.Error -> {
                    isLoading = false
                    error = state.message
                    android.util.Log.e("LoginScreen", "Sign-in error: ${state.message}")
                }
                is AuthViewModel.AuthState.Loading -> {
                    isLoading = true
                    error = null
                    android.util.Log.d("LoginScreen", "Sign-in loading...")
                }
                is AuthViewModel.AuthState.ResetEmailSent -> {
                    isLoading = false
                    resetEmailSent = true
                    error = null
                    android.util.Log.d("LoginScreen", "Password reset email sent")
                }
                else -> {
                    isLoading = false
                    error = null
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to NOVA",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon"
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { 
                        focusManager.clearFocus()
                        if (email.isNotBlank() && password.isNotBlank()) {
                            viewModel.signIn(email, password)
                        }
                    }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Icon"
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide Password" else "Show Password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            // Forgot password link
            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Forgot Password?")
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = error != null) {
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = { viewModel.signIn(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Sign In")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Divider with "OR" text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Google Sign In Button
            OutlinedButton(
                onClick = {
                    try {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    } catch (e: Exception) {
                        android.util.Log.e("LoginScreen", "Error launching Google Sign In", e)
                        error = "Error launching Google Sign In: ${e.message}"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black,
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.LightGray),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Icon",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Continue with Google", 
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Guest Login Button
            OutlinedButton(
                onClick = { viewModel.signInAnonymously() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black,
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.LightGray),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = "Guest Icon",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Continue as Guest", 
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextButton(onClick = onNavigateToSignUp) {
                    Text("Sign Up")
                }
            }

            // Test mode button - only for development
            if (onTestPlayer != {}) {
                Spacer(modifier = Modifier.height(48.dp))
                
                OutlinedButton(
                onClick = onTestPlayer,
                modifier = Modifier.fillMaxWidth()
            ) {
                    Text("Test Player (Dev Only)")
                }
            }
        }
    }
    
    // Password reset dialog
    if (showResetDialog) {
        val dialogContext = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { 
                showResetDialog = false 
                resetEmailSent = false
            },
            title = { Text("Reset Password") },
            text = {
                Column {
                    if (resetEmailSent) {
                        Text(
                            "Password reset email sent to $resetEmail. Check your inbox.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Enter your email address and we'll send you a link to reset your password.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (resetEmail.isNotBlank()) {
                                        viewModel.resetPassword(resetEmail)
                                    }
                                }
                            ),
                            isError = error != null && resetEmail.isNotBlank()
                        )
                        
                        // Show error message if there is one
                        if (error != null) {
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (resetEmailSent) {
                            showResetDialog = false
                            resetEmailSent = false
                        } else {
                            if (resetEmail.isNotBlank()) {
                                android.util.Log.d("LoginScreen", "Sending password reset email to: $resetEmail")
                                viewModel.resetPassword(resetEmail)
                                // Show a toast to provide immediate feedback
                                android.widget.Toast.makeText(
                                    dialogContext,
                                    "Sending password reset email...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    dialogContext,
                                    "Please enter your email address",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = resetEmailSent || resetEmail.isNotBlank()
                ) {
                    Text(if (resetEmailSent) "Close" else "Send Reset Link")
                }
            },
            dismissButton = {
                if (!resetEmailSent) {
                    TextButton(onClick = { 
                        showResetDialog = false
                        error = null  // Clear any error when closing
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
} 