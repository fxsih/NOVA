package com.nova.music.data.repository

import com.nova.music.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signUp(email: String, password: String, username: String): User
    suspend fun signIn(email: String, password: String): User
    suspend fun signInWithGoogle(idToken: String): User
    suspend fun signInAnonymously(): User
    suspend fun signOut()
    suspend fun updateProfile(username: String, profilePictureUrl: String?)
    suspend fun resetPassword(email: String)
    suspend fun updatePassword(newPassword: String)
    fun getCurrentUser(): Flow<User?>
    fun isUserSignedIn(): Flow<Boolean>
} 