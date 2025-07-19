package com.nova.music.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.User
import com.nova.music.data.repository.AuthRepository
import com.nova.music.util.MusicServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val musicServiceManager: MusicServiceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect {
                _currentUser.value = it
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                android.util.Log.d("AuthViewModel", "Attempting to sign in with email: $email")
                val user = authRepository.signIn(email, password)
                _authState.value = AuthState.Success(user)
                android.util.Log.d("AuthViewModel", "Sign in successful for user: ${user.email}")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Sign in failed: ${e.message}", e)
                val msg = e.message?.lowercase() ?: "Sign in failed"
                val mapped = when {
                    "password" in msg && ("incorrect" in msg || "invalid" in msg || "wrong" in msg) -> "password"
                    "email" in msg && ("not found" in msg || "invalid" in msg || "no user" in msg) -> "email"
                    else -> e.message ?: "Sign in failed"
                }
                _authState.value = AuthState.Error(mapped)
            }
        }
    }
    
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                android.util.Log.d("AuthViewModel", "Attempting to sign in with Google")
                val user = authRepository.signInWithGoogle(idToken)
                _authState.value = AuthState.Success(user)
                android.util.Log.d("AuthViewModel", "Google sign in successful for user: ${user.email}")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Google sign in failed: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Google sign in failed")
            }
        }
    }
    
    fun signInAnonymously() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                android.util.Log.d("AuthViewModel", "Attempting to sign in anonymously")
                val user = authRepository.signInAnonymously()
                _authState.value = AuthState.Success(user)
                android.util.Log.d("AuthViewModel", "Anonymous sign in successful")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Anonymous sign in failed: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Anonymous sign in failed")
            }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                android.util.Log.d("AuthViewModel", "Attempting to sign up with email: $email, username: $username")
                val user = authRepository.signUp(email, password, username)
                _authState.value = AuthState.Success(user)
                android.util.Log.d("AuthViewModel", "Sign up successful for user: ${user.email}")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Sign up failed: ${e.message}", e)
                val msg = e.message?.lowercase() ?: "Sign up failed"
                val mapped = when {
                    "password" in msg && ("incorrect" in msg || "invalid" in msg || "wrong" in msg) -> "password"
                    "email" in msg && ("already" in msg || "invalid" in msg || "exists" in msg) -> "email"
                    else -> e.message ?: "Sign up failed"
                }
                _authState.value = AuthState.Error(mapped)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // First stop the music service to terminate all playback and notifications
                android.util.Log.d("AuthViewModel", "Stopping music service before signing out")
                musicServiceManager.stopMusicService(context)
                
                // Then sign out from Firebase
                android.util.Log.d("AuthViewModel", "Signing out from Firebase")
                authRepository.signOut()
                
                _authState.value = AuthState.SignedOut
                android.util.Log.d("AuthViewModel", "Sign out completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Sign out failed: ${e.message}", e)
                _authState.value = AuthState.Error(e.message ?: "Sign out failed")
            }
        }
    }
    
    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authRepository.resetPassword(email)
                _authState.value = AuthState.ResetEmailSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Password reset failed")
            }
        }
    }
    
    fun updateProfile(username: String, profilePictureUrl: String? = null) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authRepository.updateProfile(username, profilePictureUrl)
                _authState.value = AuthState.ProfileUpdated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Profile update failed")
            }
        }
    }
    
    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authRepository.updatePassword(newPassword)
                _authState.value = AuthState.PasswordUpdated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Password update failed")
            }
        }
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()
        data class Error(val message: String) : AuthState()
        object SignedOut : AuthState()
        object ResetEmailSent : AuthState()
        object ProfileUpdated : AuthState()
        object PasswordUpdated : AuthState()
        
        override fun toString(): String {
            return when (this) {
                is Idle -> "Idle"
                is Loading -> "Loading"
                is Success -> "Success(${user.email})"
                is Error -> "Error($message)"
                is SignedOut -> "SignedOut"
                is ResetEmailSent -> "ResetEmailSent"
                is ProfileUpdated -> "ProfileUpdated"
                is PasswordUpdated -> "PasswordUpdated"
            }
        }
    }
} 