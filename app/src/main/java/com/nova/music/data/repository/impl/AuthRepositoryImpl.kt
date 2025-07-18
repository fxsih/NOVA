package com.nova.music.data.repository.impl

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.nova.music.data.model.User
import com.nova.music.data.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private val currentUserFlow = MutableStateFlow<User?>(null)

    init {
        // Initialize the current user flow with the Firebase user if available
        firebaseAuth.currentUser?.let { firebaseUser ->
            currentUserFlow.value = firebaseUser.toUser()
        }
    }

    override suspend fun signUp(email: String, password: String, username: String): User {
        return try {
            android.util.Log.d("AuthRepository", "Starting signup process for email: $email")
            
            // Create user with email and password
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to create user")
            
            android.util.Log.d("AuthRepository", "Firebase user created successfully: ${firebaseUser.uid}")
            
            // Update display name
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
                android.util.Log.d("AuthRepository", "Profile updated with username: $username")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Failed to update profile: ${e.message}", e)
                // Continue even if profile update fails
            }

            // Create user document in Firestore
            val user = User(
                id = firebaseUser.uid,
                email = email,
                username = username
            )
            
            try {
                firestore.collection("users").document(firebaseUser.uid)
                    .set(user)
                    .await()
                android.util.Log.d("AuthRepository", "User document created in Firestore")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Failed to save user to Firestore: ${e.message}", e)
                // Continue even if Firestore fails
            }

            // Make sure to update the current user flow
            currentUserFlow.value = user
            android.util.Log.d("AuthRepository", "Sign up successful for user: ${user.email}")
            
            // IMPORTANT: Force sign out after sign up to ensure clean state
            try {
                android.util.Log.d("AuthRepository", "Forcing sign out after signup")
                firebaseAuth.signOut()
                currentUserFlow.value = null
                android.util.Log.d("AuthRepository", "User signed out after signup")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Failed to sign out after signup: ${e.message}", e)
            }
            
            return user
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Sign up failed: ${e.message}", e)
            throw Exception("Sign up failed: ${e.message}")
        }
    }

    override suspend fun signIn(email: String, password: String): User {
        return try {
            android.util.Log.d("AuthRepository", "Attempting to sign in: $email")
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in")
            
            android.util.Log.d("AuthRepository", "Firebase authentication successful for: ${firebaseUser.uid}")
            
            // Get user data from Firestore
            val user = try {
                val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
                
                if (userDoc.exists()) {
                    android.util.Log.d("AuthRepository", "User document found in Firestore")
                    userDoc.toObject(User::class.java) ?: firebaseUser.toUser()
                } else {
                    android.util.Log.d("AuthRepository", "User document not found in Firestore, creating new one")
                    // If user document doesn't exist, create one
                    val newUser = firebaseUser.toUser()
                    firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                    newUser
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Error retrieving user data from Firestore: ${e.message}", e)
                // If Firestore fails, fall back to Firebase Auth user data
                firebaseUser.toUser()
            }
            
            currentUserFlow.value = user
            android.util.Log.d("AuthRepository", "Sign in successful for user: ${user.email}")
            return user
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Sign in failed: ${e.message}", e)
            throw Exception("Sign in failed: ${e.message}")
        }
    }

    override suspend fun signInWithGoogle(idToken: String): User {
        try {
            android.util.Log.d("AuthRepository", "Attempting to sign in with Google")
            
            // Create a credential with the Google ID token
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            
            // Sign in with the credential
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")
            
            android.util.Log.d("AuthRepository", "Google authentication successful for: ${firebaseUser.uid}")
            
            // Check if this is a new user
            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false
            
            // Get or create user data
            val user = try {
                val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
                
                if (userDoc.exists()) {
                    android.util.Log.d("AuthRepository", "User document found in Firestore for Google user")
                    userDoc.toObject(User::class.java) ?: firebaseUser.toUser()
                } else {
                    android.util.Log.d("AuthRepository", "User document not found in Firestore for Google user, creating new one")
                    // If user document doesn't exist, create one
                    val newUser = firebaseUser.toUser()
                    firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                    newUser
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Error retrieving user data from Firestore for Google user: ${e.message}", e)
                // If Firestore fails, fall back to Firebase Auth user data
                firebaseUser.toUser()
            }
            
            currentUserFlow.value = user
            android.util.Log.d("AuthRepository", "Google sign in successful for user: ${user.email}")
            return user
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google sign in failed: ${e.message}", e)
            throw Exception("Google sign in failed: ${e.message}")
        }
    }
    
    override suspend fun signInAnonymously(): User {
        try {
            android.util.Log.d("AuthRepository", "Attempting to sign in anonymously")
            
            // Sign in anonymously
            val authResult = firebaseAuth.signInAnonymously().await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in anonymously")
            
            android.util.Log.d("AuthRepository", "Anonymous authentication successful for: ${firebaseUser.uid}")
            
            // Create a guest user
            val guestUser = User(
                id = firebaseUser.uid,
                email = "",
                username = "Guest User",
                profilePictureUrl = null
            )
            
            // Store guest user in Firestore
            try {
                firestore.collection("users").document(firebaseUser.uid)
                    .set(guestUser)
                    .await()
                android.util.Log.d("AuthRepository", "Guest user document created in Firestore")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Failed to save guest user to Firestore: ${e.message}", e)
                // Continue even if Firestore fails
            }
            
            currentUserFlow.value = guestUser
            android.util.Log.d("AuthRepository", "Anonymous sign in successful")
            return guestUser
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Anonymous sign in failed: ${e.message}", e)
            throw Exception("Anonymous sign in failed: ${e.message}")
        }
    }

    override suspend fun signOut() {
        try {
            firebaseAuth.signOut()
            currentUserFlow.value = null
        } catch (e: Exception) {
            throw Exception("Sign out failed: ${e.message}")
        }
    }

    override suspend fun updateProfile(username: String, profilePictureUrl: String?) {
        try {
            val firebaseUser = firebaseAuth.currentUser ?: throw Exception("User not signed in")
            
            // Update Firebase Auth profile
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .apply { 
                    profilePictureUrl?.let { setPhotoUri(android.net.Uri.parse(it)) }
                }
                .build()
            
            firebaseUser.updateProfile(profileUpdates).await()
            
            // Update Firestore document
            val updates = hashMapOf<String, Any>(
                "username" to username
            )
            profilePictureUrl?.let { updates["profilePictureUrl"] = it }
            
            firestore.collection("users").document(firebaseUser.uid)
                .update(updates)
                .await()
            
            // Update local user
            currentUserFlow.value = currentUserFlow.value?.copy(
                username = username,
                profilePictureUrl = profilePictureUrl
            )
        } catch (e: Exception) {
            throw Exception("Update profile failed: ${e.message}")
        }
    }
    
    override suspend fun resetPassword(email: String) {
        try {
            android.util.Log.d("AuthRepository", "Attempting to send password reset email to: $email")
            firebaseAuth.sendPasswordResetEmail(email).await()
            android.util.Log.d("AuthRepository", "Password reset email sent successfully to: $email")
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Password reset failed: ${e.message}", e)
            
            // Provide more specific error messages based on the exception
            val errorMessage = when {
                e.message?.contains("no user record", ignoreCase = true) == true -> 
                    "No account exists with this email address."
                e.message?.contains("invalid email", ignoreCase = true) == true -> 
                    "Please enter a valid email address."
                e.message?.contains("blocked", ignoreCase = true) == true -> 
                    "Too many attempts. Please try again later."
                else -> "Password reset failed: ${e.message}"
            }
            
            throw Exception(errorMessage)
        }
    }
    
    override suspend fun updatePassword(newPassword: String) {
        try {
            val firebaseUser = firebaseAuth.currentUser ?: throw Exception("User not signed in")
            
            // Check if user is anonymous
            if (firebaseUser.isAnonymous) {
                throw Exception("Anonymous users cannot change their password")
            }
            
            // Check if user is signed in with a provider that supports password update
            val providers = firebaseUser.providerData.map { it.providerId }
            if (!providers.contains("password")) {
                throw Exception("Password change is only available for email/password accounts")
            }
            
            // Update the password
            android.util.Log.d("AuthRepository", "Attempting to update password")
            firebaseUser.updatePassword(newPassword).await()
            android.util.Log.d("AuthRepository", "Password updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Password update failed: ${e.message}", e)
            
            // Provide more specific error messages based on the exception
            val errorMessage = when {
                e.message?.contains("requires recent authentication", ignoreCase = true) == true -> 
                    "For security reasons, please sign in again before changing your password"
                e.message?.contains("weak password", ignoreCase = true) == true -> 
                    "Please use a stronger password (at least 6 characters)"
                else -> "Password update failed: ${e.message}"
            }
            
            throw Exception(errorMessage)
        }
    }

    override fun getCurrentUser(): Flow<User?> = currentUserFlow

    override fun isUserSignedIn(): Flow<Boolean> = currentUserFlow.map { it != null }
    
    // Helper extension function to convert FirebaseUser to User
    private fun FirebaseUser.toUser(): User {
        return User(
            id = uid,
            email = email ?: "",
            username = displayName ?: email?.substringBefore('@') ?: "",
            profilePictureUrl = photoUrl?.toString()
        )
    }
} 