package com.nova.music.data.repository.impl

import com.nova.music.data.model.User
import com.nova.music.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor() : AuthRepository {
    private val currentUser = MutableStateFlow<User?>(null)

    override suspend fun signUp(email: String, password: String, username: String): User {
        // Implement actual sign up logic here
        // For now, return a mock user
        val user = User(
            id = "1",
            email = email,
            username = username
        )
        currentUser.value = user
        return user
    }

    override suspend fun signIn(email: String, password: String): User {
        // Implement actual sign in logic here
        // For now, return a mock user
        val user = User(
            id = "1",
            email = email,
            username = "User"
        )
        currentUser.value = user
        return user
    }

    override suspend fun signOut() {
        currentUser.value = null
    }

    override suspend fun updateProfile(username: String, profilePictureUrl: String?) {
        currentUser.value = currentUser.value?.copy(
            username = username,
            profilePictureUrl = profilePictureUrl
        )
    }

    override fun getCurrentUser(): Flow<User?> = currentUser

    override fun isUserSignedIn(): Flow<Boolean> = MutableStateFlow(currentUser.value != null)
} 