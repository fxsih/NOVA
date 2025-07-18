# Setting Up Firebase Authentication for NOVA Music App

This guide will help you set up Firebase Authentication for the NOVA Music App. Follow these steps to configure Firebase Authentication and enable user sign-up, login, and profile management.

## Prerequisites

- A Google account
- Android Studio installed
- NOVA Music App codebase

## Step 1: Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click on "Add project"
3. Enter a project name (e.g., "NOVA Music App")
4. Follow the prompts to set up your project
5. Enable Google Analytics if desired (recommended)
6. Click "Create project"

## Step 2: Register Your Android App with Firebase

1. In the Firebase Console, click on the Android icon to add an Android app
2. Enter your app's package name: `com.nova.music`
3. Enter a nickname for your app (optional)
4. Enter your app's signing certificate SHA-1 (optional for development, required for production)
5. Click "Register app"

## Step 3: Download and Add the Configuration File

1. Download the `google-services.json` file
2. Replace the placeholder `google-services.json` file in your project's `app` directory with the downloaded file

## Step 4: Enable Authentication Methods

1. In the Firebase Console, go to "Authentication" from the left sidebar
2. Click on the "Sign-in method" tab
3. Enable the following authentication methods:
   - Email/Password
   - Google (optional)
   - Other methods as needed

## Step 5: Configure Firebase Authentication in Your App

The app is already configured to use Firebase Authentication. The following components have been implemented:

- `AuthRepository` and `AuthRepositoryImpl` for handling authentication operations
- `AuthViewModel` for managing authentication state
- Login and Signup screens
- Profile management screen
- Navigation with authentication state handling

## Step 6: Test Authentication

1. Build and run the app
2. Test the sign-up functionality
3. Test the login functionality
4. Test the password reset functionality
5. Test the profile management functionality

## Troubleshooting

If you encounter any issues:

1. Ensure the `google-services.json` file is correctly placed in the `app` directory
2. Check that the package name in your app matches the one registered in Firebase
3. Verify that the authentication methods are enabled in the Firebase Console
4. Check the Logcat for any Firebase-related errors

## Next Steps

After setting up Firebase Authentication, you can:

1. Implement user data synchronization with Firestore
2. Add social login options (Google, Facebook, etc.)
3. Implement email verification
4. Add more profile management features

## Resources

- [Firebase Authentication Documentation](https://firebase.google.com/docs/auth)
- [Firebase Android Setup Guide](https://firebase.google.com/docs/android/setup)
- [Firebase Authentication Android Guide](https://firebase.google.com/docs/auth/android/start) 