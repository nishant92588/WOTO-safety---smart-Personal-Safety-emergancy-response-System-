package com.example.woto_safety.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private const val TAG = "WotoFirebase"
private const val PREFS_NAME = "woto_safety_prefs"
private const val KEY_IS_LOGGED_IN = "is_logged_in"
private const val KEY_USER_NAME = "user_name"
private const val KEY_USER_PHONE = "user_phone"
private const val KEY_USER_EMAIL = "user_email"
private const val KEY_SOS_NUMBER_1 = "sos_number_1"
private const val KEY_SOS_NUMBER_2 = "sos_number_2"

object FirebaseAuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun isUserLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val localLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        return localLoggedIn || (auth.currentUser != null)
    }

    /**
     * Firebase Email/Password Sign Up & Firestore Profile Creation
     */
    fun signUpUser(
        context: Context,
        name: String,
        phone: String,
        email: String,
        emergencyNumber1: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanEmail = email.trim()

        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid ?: ""

                // 1. Update Display Name in Firebase Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                firebaseUser?.updateProfile(profileUpdates)

                // 2. Save User Document to Cloud Firestore
                val userMap = hashMapOf(
                    "uid" to uid,
                    "name" to name,
                    "phone" to phone,
                    "email" to cleanEmail,
                    "sosNumber1" to emergencyNumber1,
                    "updatedAt" to System.currentTimeMillis()
                )

                if (uid.isNotBlank()) {
                    firestore.collection("users").document(uid)
                        .set(userMap, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "Firestore Profile saved successfully for UID: $uid")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore set error: ${e.message}", e)
                        }
                }

                // 3. Save locally in SharedPreferences
                prefs.edit().apply {
                    putBoolean(KEY_IS_LOGGED_IN, true)
                    putString(KEY_USER_NAME, name)
                    putString(KEY_USER_PHONE, phone)
                    putString(KEY_USER_EMAIL, cleanEmail)
                    if (emergencyNumber1.isNotBlank()) {
                        putString(KEY_SOS_NUMBER_1, emergencyNumber1)
                    }
                    apply()
                }

                onResult(true, "Firebase Account Created & Saved Successfully!")
            }
            .addOnFailureListener { exception ->
                val errorMsg = exception.localizedMessage ?: "Sign up failed"
                Log.e(TAG, "Firebase Auth sign-up error: $errorMsg", exception)
                onResult(false, errorMsg)
            }
    }

    /**
     * Firebase Email/Password Login
     */
    fun loginUser(
        context: Context,
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanEmail = email.trim()

        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid ?: ""

                // Fetch User Profile & Emergency Numbers from Firestore
                if (uid.isNotBlank()) {
                    firestore.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val firestoreName = doc.getString("name") ?: user?.displayName ?: cleanEmail.substringBefore("@")
                            val firestorePhone = doc.getString("phone") ?: ""
                            val firestoreSos1 = doc.getString("sosNumber1") ?: ""
                            val firestoreSos2 = doc.getString("sosNumber2") ?: ""

                            prefs.edit().apply {
                                putBoolean(KEY_IS_LOGGED_IN, true)
                                putString(KEY_USER_NAME, firestoreName)
                                putString(KEY_USER_PHONE, firestorePhone)
                                putString(KEY_USER_EMAIL, user?.email ?: cleanEmail)
                                if (firestoreSos1.isNotBlank()) putString(KEY_SOS_NUMBER_1, firestoreSos1)
                                if (firestoreSos2.isNotBlank()) putString(KEY_SOS_NUMBER_2, firestoreSos2)
                                apply()
                            }
                            onResult(true, "Logged in via Firebase Auth & Database Sync!")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore read error: ${e.message}", e)
                            saveLocalLogin(prefs, cleanEmail, user?.displayName)
                            onResult(true, "Logged in via Firebase Auth!")
                        }
                } else {
                    saveLocalLogin(prefs, cleanEmail, user?.displayName)
                    onResult(true, "Logged in via Firebase Auth!")
                }
            }
            .addOnFailureListener { exception ->
                val errorMsg = exception.localizedMessage ?: "Invalid email or password"
                Log.e(TAG, "Firebase Auth login error: $errorMsg", exception)
                onResult(false, errorMsg)
            }
    }

    private fun saveLocalLogin(prefs: SharedPreferences, email: String, displayName: String?) {
        val name = displayName ?: email.substringBefore("@")
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    /**
     * Firebase Sign Out
     */
    fun logoutUser(context: Context) {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error: ${e.message}", e)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
    }

    /**
     * Log SOS Emergency Event to Firebase Firestore
     */
    fun logEmergencyAlert(
        context: Context,
        locationUrl: String,
        sosNumber1: String,
        sosNumber2: String
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_USER_NAME, "Anonymous User") ?: "Anonymous User"
            val phone = prefs.getString(KEY_USER_PHONE, "") ?: ""
            val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
            val uid = auth.currentUser?.uid ?: "guest_device"

            val alertData = hashMapOf(
                "uid" to uid,
                "userName" to name,
                "userPhone" to phone,
                "userEmail" to email,
                "locationUrl" to locationUrl,
                "sosNumber1" to sosNumber1,
                "sosNumber2" to sosNumber2,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("emergency_alerts")
                .add(alertData)
                .addOnSuccessListener { doc ->
                    Log.d(TAG, "Emergency alert logged in Firestore with ID: ${doc.id}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore emergency alert log error: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency alert error: ${e.message}", e)
        }
    }
}
