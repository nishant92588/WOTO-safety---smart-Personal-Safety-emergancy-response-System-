package com.example.woto_safety

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.woto_safety.data.FirebaseAuthRepository
import com.example.woto_safety.ui.AuthScreen
import com.example.woto_safety.ui.SosScreen
import com.example.woto_safety.ui.theme.WOTO_safetyTheme

private const val PREFS_NAME = "woto_safety_prefs"
private const val KEY_IS_LOGGED_IN = "is_logged_in"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            WOTO_safetyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    var isLoggedIn by remember {
                        mutableStateOf(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
                    }
                    var skipAuth by remember { mutableStateOf(false) }

                    if (isLoggedIn || skipAuth) {
                        SosScreen(
                            onLogoutRequested = {
                                FirebaseAuthRepository.logoutUser(this@MainActivity)
                                isLoggedIn = false
                                skipAuth = false
                            }
                        )
                    } else {
                        AuthScreen(
                            onAuthSuccess = {
                                isLoggedIn = true
                            },
                            onSkipToSos = {
                                skipAuth = true
                            }
                        )
                    }
                }
            }
        }
    }
}
