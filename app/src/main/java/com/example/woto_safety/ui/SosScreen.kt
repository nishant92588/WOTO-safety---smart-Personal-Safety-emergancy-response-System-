package com.example.woto_safety.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.woto_safety.data.FirebaseAuthRepository
import com.example.woto_safety.ui.theme.WOTO_safetyTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay

private const val PREFS_NAME = "woto_safety_prefs"
private const val KEY_SOS_NUMBER_1 = "sos_number_1"
private const val KEY_SOS_NUMBER_2 = "sos_number_2"
private const val KEY_USER_NAME = "user_name"
private const val KEY_SHAKE_ENABLED = "shake_detection_enabled"

data class EmergencyContact(
    val name: String,
    val relation: String,
    val phone: String,
    val icon: ImageVector = Icons.Default.Person
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(
    onLogoutRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val userName = remember { prefs.getString(KEY_USER_NAME, "") ?: "" }

    // State management for Emergency Numbers
    var sosNumber1 by remember { mutableStateOf(prefs.getString(KEY_SOS_NUMBER_1, "") ?: "") }
    var sosNumber2 by remember { mutableStateOf(prefs.getString(KEY_SOS_NUMBER_2, "") ?: "") }
    var showNumberSetupDialog by remember { mutableStateOf(false) }

    // Temporary inputs inside setup dialog
    var inputNumber1 by remember { mutableStateOf("") }
    var inputNumber2 by remember { mutableStateOf("") }

    // SOS State
    var isSosActive by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(5) }
    var isSirenActive by remember { mutableStateOf(false) }
    var liveLocationUrl by remember { mutableStateOf("Fetching live location...") }

    // Permissions State
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission Launcher for requesting permissions automatically on app open
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasCallPermission = permissions[Manifest.permission.CALL_PHONE] == true
        hasSmsPermission = permissions[Manifest.permission.SEND_SMS] == true
    }

    // Request permissions automatically on app open
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)

        // Show setup dialog if primary SOS number is missing
        if (sosNumber1.isBlank() || sosNumber2.isBlank()) {
            inputNumber1 = sosNumber1
            inputNumber2 = sosNumber2
            showNumberSetupDialog = true
        }
    }

    // Handle Siren Audio Playback
    LaunchedEffect(isSirenActive) {
        if (isSirenActive) {
            SirenManager.start(context)
        } else {
            SirenManager.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SirenManager.stop()
        }
    }

    var isShakeDetectionEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_SHAKE_ENABLED, true))
    }

    // Register Accelerometer Listener to detect 3 strong shakes
    DisposableEffect(isShakeDetectionEnabled, isSosActive, showCountdownDialog) {
        if (!isShakeDetectionEnabled || isSosActive || showCountdownDialog) {
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val shakeDetector = ShakeDetector {
            if (sosNumber1.isBlank()) {
                Toast.makeText(context, "📳 Shake detected! Please set Emergency Number #1 first!", Toast.LENGTH_LONG).show()
                showNumberSetupDialog = true
            } else {
                Toast.makeText(context, "📳 3 Shakes Detected! Starting SOS Countdown...", Toast.LENGTH_LONG).show()
                countdownSeconds = 5
                showCountdownDialog = true
                vibrateDevice(context, 400L)
            }
        }

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(shakeDetector)
        }
    }

    // Handle SOS Countdown logic
    LaunchedEffect(showCountdownDialog, countdownSeconds) {
        if (showCountdownDialog && countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
            vibrateDevice(context, 100L)
        } else if (showCountdownDialog && countdownSeconds == 0) {
            showCountdownDialog = false
            isSosActive = true
            vibrateDevice(context, 800L)

            // Trigger full SOS: Call Number #1 & Send Location SMS to Both #1 & #2
            dispatchFullSosTrigger(context, sosNumber1, sosNumber2) { fetchedUrl ->
                liveLocationUrl = fetchedUrl
            }
        }
    }

    // List of additional emergency contact cards
    val emergencyContacts = remember(sosNumber1, sosNumber2) {
        listOf(
            EmergencyContact("Primary Call Target (#1)", "Main Emergency Number", sosNumber1.ifBlank { "Not set" }, Icons.Default.Phone),
            EmergencyContact("Secondary Location Target (#2)", "SMS Alert Contact", sosNumber2.ifBlank { "Not set" }, Icons.Default.Person),
            EmergencyContact("Police Helpline", "Local Authority", "112", Icons.Default.LocalPolice),
            EmergencyContact("Ambulance Service", "Medical Helpline", "108", Icons.Default.LocalHospital)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "WOTO Safety",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (userName.isNotBlank()) {
                                Text(
                                    text = "Hello, $userName",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSosActive) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSosActive) Color.Red else Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSosActive) "SOS ACTIVE" else "PROTECTED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSosActive) Color.Red else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (onLogoutRequested != null) {
                        IconButton(onClick = onLogoutRequested) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Log Out",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // Permission Status Warning Banner (if missing permissions)
            if (!hasLocationPermission || !hasCallPermission || !hasSmsPermission) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column {
                                    Text(
                                        text = "Permissions Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Allow Location, Call & SMS permissions for full SOS action.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Button(
                                onClick = { permissionLauncher.launch(permissionsToRequest) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("GRANT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Emergency Contacts Configuration Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Emergency Numbers Setup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = {
                                    inputNumber1 = sosNumber1
                                    inputNumber2 = sosNumber2
                                    showNumberSetupDialog = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Numbers",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Numbers", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "1. Primary SOS Call & SMS:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sosNumber1.ifBlank { "⚠️ Not Set (Tap Edit)" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (sosNumber1.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "2. Secondary Location SMS:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sosNumber2.ifBlank { "⚠️ Not Set" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (sosNumber2.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // SOS Active Banner Notification
            item {
                AnimatedVisibility(
                    visible = isSosActive,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "EMERGENCY ALERT BROADCASTING",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Emergency call initiated to $sosNumber1 and live GPS location sent to both $sosNumber1 & $sosNumber2.",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Location URL: $liveLocationUrl",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { makeEmergencyCall(context, sosNumber1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call $sosNumber1",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-call #1", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        isSosActive = false
                                        Toast.makeText(context, "SOS Deactivated", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Deactivate SOS")
                                }
                            }
                        }
                    }
                }
            }

            // Main One Tap SOS Button Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OneTapSosButtonSection(
                    isSosActive = isSosActive,
                    onSosClicked = {
                        if (sosNumber1.isBlank()) {
                            Toast.makeText(context, "Please set Emergency Number #1 first!", Toast.LENGTH_SHORT).show()
                            showNumberSetupDialog = true
                        } else if (!isSosActive) {
                            countdownSeconds = 5
                            showCountdownDialog = true
                            vibrateDevice(context, 200L)
                        } else {
                            isSosActive = false
                            Toast.makeText(context, "SOS Alert Stopped", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Shake Detection Feature Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isShakeDetectionEnabled)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isShakeDetectionEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Shake Gesture",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Shake 3 Times for SOS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isShakeDetectionEnabled)
                                        "Active: Shake phone 3 times to trigger SOS"
                                    else
                                        "Disabled: Tap toggle to activate shake gesture",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isShakeDetectionEnabled,
                            onCheckedChange = { checked ->
                                isShakeDetectionEnabled = checked
                                prefs.edit().putBoolean(KEY_SHAKE_ENABLED, checked).apply()
                                if (checked) {
                                    Toast.makeText(context, "Shake Detection Activated! Shake 3 times for SOS", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Quick Safety Actions Grid
            item {
                Text(
                    text = "Quick Emergency Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Call Primary (#1) -> ONLY MAKES PHONE CALL
                    QuickActionCard(
                        title = "Call Primary (#1)",
                        subtitle = if (sosNumber1.isNotBlank()) sosNumber1 else "Set Number",
                        icon = Icons.Default.Call,
                        iconBgColor = Color(0xFF1E88E5),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (sosNumber1.isNotBlank()) {
                                makeEmergencyCall(context, sosNumber1)
                            } else {
                                showNumberSetupDialog = true
                            }
                        }
                    )

                    // Share Location -> ONLY SHARES LOCATION VIA INTENT (NO CALL)
                    QuickActionCard(
                        title = "Share Location",
                        subtitle = "Send GPS Map",
                        icon = Icons.Default.LocationOn,
                        iconBgColor = Color(0xFF43A047),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Toast.makeText(context, "Fetching live location...", Toast.LENGTH_SHORT).show()
                            fetchLiveLocation(context) { fetchedUrl ->
                                liveLocationUrl = fetchedUrl
                                shareCurrentLocation(context, fetchedUrl)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Loud Siren Action Card
                    QuickActionCard(
                        title = if (isSirenActive) "Stop Siren" else "Loud Siren",
                        subtitle = if (isSirenActive) "Siren Playing" else "Attract Attention",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconBgColor = if (isSirenActive) Color(0xFFD32F2F) else Color(0xFFFB8C00),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isSirenActive = !isSirenActive
                            vibrateDevice(context, 300L)
                            Toast.makeText(
                                context,
                                if (isSirenActive) "🔊 Loud Emergency Siren Playing!" else "🔇 Siren Off",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // SMS Alert -> ONLY SENDS SMS TO BOTH TARGETS (NO CALL)
                    QuickActionCard(
                        title = "SMS Alert",
                        subtitle = "Text Both Targets",
                        icon = Icons.Default.NotificationsActive,
                        iconBgColor = Color(0xFF8E24AA),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (sosNumber1.isBlank() && sosNumber2.isBlank()) {
                                Toast.makeText(context, "Please configure Emergency Numbers first!", Toast.LENGTH_SHORT).show()
                                showNumberSetupDialog = true
                            } else {
                                Toast.makeText(context, "Sending Location SMS to contacts...", Toast.LENGTH_SHORT).show()
                                fetchLiveLocation(context) { fetchedUrl ->
                                    liveLocationUrl = fetchedUrl
                                    val message = "🚨 EMERGENCY SOS ALERT! I need help. My live GPS location: $fetchedUrl"

                                    var sent1 = false
                                    var sent2 = false
                                    if (sosNumber1.isNotBlank()) {
                                        sent1 = sendDirectSms(context, sosNumber1, message)
                                    }
                                    if (sosNumber2.isNotBlank()) {
                                        sent2 = sendDirectSms(context, sosNumber2, message)
                                    }

                                    if (sent1 || sent2) {
                                        Toast.makeText(context, "🚨 SMS Alert sent to emergency contacts!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Opening SMS app...", Toast.LENGTH_SHORT).show()
                                        val target = if (sosNumber1.isNotBlank()) sosNumber1 else sosNumber2
                                        openSmsAppFallback(context, target, message)
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Emergency Contacts Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Emergency Contacts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = {
                        inputNumber1 = sosNumber1
                        inputNumber2 = sosNumber2
                        showNumberSetupDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Configure Numbers",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(emergencyContacts) { contact ->
                EmergencyContactCard(
                    contact = contact,
                    onCallClicked = {
                        if (contact.phone != "Not set") {
                            makeEmergencyCall(context, contact.phone)
                        } else {
                            showNumberSetupDialog = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Emergency Contact Numbers Setup Dialog
    if (showNumberSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (sosNumber1.isNotBlank()) {
                    showNumberSetupDialog = false
                } else {
                    Toast.makeText(context, "Please enter at least Primary SOS Number #1", Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Configure Emergency Numbers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Specify the emergency contacts that will be triggered automatically when you tap SOS.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputNumber1,
                        onValueChange = { inputNumber1 = it },
                        label = { Text("Number 1: SOS Call & Location Target") },
                        placeholder = { Text("e.g. +1 234 567 8900 or 112") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Call, contentDescription = null)
                        }
                    )

                    OutlinedTextField(
                        value = inputNumber2,
                        onValueChange = { inputNumber2 = it },
                        label = { Text("Number 2: Secondary Location Target") },
                        placeholder = { Text("e.g. +1 987 654 3210") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputNumber1.trim().isBlank()) {
                            Toast.makeText(context, "Please enter Number 1 (Primary Call Target)", Toast.LENGTH_SHORT).show()
                        } else {
                            sosNumber1 = inputNumber1.trim()
                            sosNumber2 = inputNumber2.trim()
                            prefs.edit().apply {
                                putString(KEY_SOS_NUMBER_1, sosNumber1)
                                putString(KEY_SOS_NUMBER_2, sosNumber2)
                                apply()
                            }
                            showNumberSetupDialog = false
                            Toast.makeText(context, "Emergency Numbers Saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("SAVE EMERGENCY NUMBERS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (sosNumber1.isNotBlank()) {
                    OutlinedButton(
                        onClick = { showNumberSetupDialog = false }
                    ) {
                        Text("CANCEL")
                    }
                }
            }
        )
    }

    // Countdown Dialog Overlay before sending alert
    if (showCountdownDialog) {
        AlertDialog(
            onDismissRequest = {
                showCountdownDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Sending SOS Alert!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Calling $sosNumber1 and sending GPS location to both numbers in:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular / Big Countdown display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Text(
                            text = "$countdownSeconds",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (countdownSeconds / 5f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap CANCEL if this was pressed by mistake.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCountdownDialog = false
                        isSosActive = true
                        vibrateDevice(context, 500L)

                        dispatchFullSosTrigger(context, sosNumber1, sosNumber2) { fetchedUrl ->
                            liveLocationUrl = fetchedUrl
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("TRIGGER NOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCountdownDialog = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CANCEL")
                }
            }
        )
    }
}

/**
 * High-Decibel Emergency Siren Audio Synthesizer & Alarm Player Manager
 */
object SirenManager {
    private var isRunning = false
    private var audioTrack: AudioTrack? = null
    private var currentRingtone: Ringtone? = null
    private var thread: Thread? = null

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true

        val appContext = context.applicationContext

        // 1. Maximize Alarm Volume
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Play System Alarm Ringtone
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(appContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            currentRingtone = ringtone
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Sweeping High-Decibel Police/Emergency Siren Audio Generator (600Hz - 1400Hz)
        thread = Thread {
            try {
                val sampleRate = 44100
                val minBuff = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val buffSize = if (minBuff > 0) minBuff else 4096

                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(buffSize)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_ALARM,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buffSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack = track
                track.play()

                val samples = ShortArray(buffSize)
                var ph = 0.0
                var freq = 600.0
                var ascending = true

                while (isRunning) {
                    for (i in samples.indices) {
                        if (ascending) {
                            freq += 0.08
                            if (freq >= 1400.0) ascending = false
                        } else {
                            freq -= 0.08
                            if (freq <= 600.0) ascending = true
                        }
                        ph += 2.0 * Math.PI * freq / sampleRate
                        samples[i] = (Math.sin(ph) * Short.MAX_VALUE * 0.9).toInt().toShort()
                    }
                    track.write(samples, 0, samples.size)
                }

                track.stop()
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.apply { start() }
    }

    fun stop() {
        isRunning = false
        try {
            currentRingtone?.stop()
            currentRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        thread = null
    }

    fun isPlaying(): Boolean = isRunning
}

/**
 * Prominent One-Tap SOS Circular Button Component with Animated Halo Pulsing Rings
 */
@Composable
fun OneTapSosButtonSection(
    isSosActive: Boolean,
    onSosClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    // Pulsing animation for outer halo ring
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Outer Pulsing Halo Ring
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(if (!isSosActive) pulseScale else 1.3f)
                    .clip(CircleShape)
                    .background(
                        if (isSosActive) Color.Red.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                    )
            )

            // Inner Accent Ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSosActive) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
            )

            // Core One-Tap SOS Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isSosActive) listOf(
                                Color(0xFFB71C1C),
                                Color(0xFFD32F2F)
                            ) else listOf(
                                Color(0xFFE53935),
                                Color(0xFFC62828)
                            )
                        )
                    )
                    .clickable { onSosClicked() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "SOS Shield",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isSosActive) "TAP TO STOP" else "ONE TAP SOS",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSosActive) "🚨 Emergency call & location broadcast active!" else "Tap once to trigger immediate call and location SMS",
            fontSize = 13.sp,
            color = if (isSosActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSosActive) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Card for Quick Actions like Call Police, Share Location, Siren, etc.
 */
@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBgColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Card for displaying an Emergency Contact with direct Call button
 */
@Composable
fun EmergencyContactCard(
    contact: EmergencyContact,
    onCallClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = contact.icon,
                        contentDescription = contact.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${contact.relation} • ${contact.phone}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onCallClicked,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Call ${contact.name}",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// Utility Helper Functions

private fun fetchLiveLocation(
    context: Context,
    onLocationFetched: (String) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    if (hasFineLoc || hasCoarseLoc) {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val url = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                        onLocationFetched(url)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            val url = if (lastLoc != null) {
                                "https://maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}"
                            } else {
                                "https://maps.google.com/?q=28.6139,77.2090"
                            }
                            onLocationFetched(url)
                        }.addOnFailureListener {
                            onLocationFetched("https://maps.google.com/?q=28.6139,77.2090")
                        }
                    }
                }
                .addOnFailureListener {
                    onLocationFetched("https://maps.google.com/?q=28.6139,77.2090")
                }
        } catch (e: SecurityException) {
            onLocationFetched("https://maps.google.com/?q=28.6139,77.2090")
        }
    } else {
        onLocationFetched("https://maps.google.com/?q=28.6139,77.2090")
    }
}

private fun dispatchFullSosTrigger(
    context: Context,
    number1: String,
    number2: String,
    onLocationFetched: (String) -> Unit
) {
    Toast.makeText(context, "🚨 Fetching location & triggering SOS...", Toast.LENGTH_SHORT).show()

    fetchLiveLocation(context) { locationUrl ->
        onLocationFetched(locationUrl)

        // Log Emergency Event to Firebase Firestore
        FirebaseAuthRepository.logEmergencyAlert(context, locationUrl, number1, number2)

        val message = "🚨 EMERGENCY SOS ALERT! I need urgent help! My live GPS location: $locationUrl"

        // 1. Send SMS to Number #1
        var sms1Sent = false
        if (number1.isNotBlank()) {
            sms1Sent = sendDirectSms(context, number1, message)
        }

        // 2. Send SMS to Number #2
        var sms2Sent = false
        if (number2.isNotBlank()) {
            sms2Sent = sendDirectSms(context, number2, message)
        }

        if (sms1Sent || sms2Sent) {
            Toast.makeText(context, "📩 Location SMS sent to emergency contacts!", Toast.LENGTH_SHORT).show()
        } else if (number1.isNotBlank() || number2.isNotBlank()) {
            Toast.makeText(context, "Opening SMS app...", Toast.LENGTH_SHORT).show()
            val target = if (number1.isNotBlank()) number1 else number2
            openSmsAppFallback(context, target, message)
        }

        // 3. Initiate Emergency Call to Number #1
        if (number1.isNotBlank()) {
            makeEmergencyCall(context, number1)
        }
    }
}

private fun sendDirectSms(context: Context, phoneNumber: String, message: String): Boolean {
    if (phoneNumber.isBlank()) return false
    return try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    context.getSystemService(SmsManager::class.java)
                } catch (e: Exception) {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun openSmsAppFallback(context: Context, phoneNumber: String, message: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch SMS app", Toast.LENGTH_SHORT).show()
    }
}

private fun makeEmergencyCall(context: Context, phoneNumber: String) {
    if (phoneNumber.isBlank()) {
        Toast.makeText(context, "No emergency phone number specified", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to initiate call", Toast.LENGTH_SHORT).show()
    }
}

private fun shareCurrentLocation(context: Context, locationUrl: String) {
    try {
        val locationMessage = "🚨 EMERGENCY! I need help. My live GPS location: $locationUrl"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EMERGENCY SOS LOCATION")
            putExtra(Intent.EXTRA_TEXT, locationMessage)
        }
        context.startActivity(Intent.createChooser(intent, "Share Emergency Location"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
    }
}

private fun vibrateDevice(context: Context, durationMs: Long) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (e: Exception) {
        // Vibrator permission or hardware optional
    }
}

/**
 * SensorEventListener to detect 3 distinct strong shake gestures within a 3-second window.
 */
private class ShakeDetector(
    private val onShake3Times: () -> Unit
) : SensorEventListener {
    private var lastShakeTime: Long = 0
    private var shakeCount: Int = 0
    private var lastWindowStart: Long = 0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = kotlin.math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        // Threshold of 2.6g for a strong intentional shake
        if (gForce > 2.6f) {
            val now = System.currentTimeMillis()

            // Debounce shakes occurring within 350ms of each other
            if (now - lastShakeTime < 350) return

            // Reset shake window if 3 seconds have elapsed
            if (now - lastWindowStart > 3000) {
                shakeCount = 0
                lastWindowStart = now
            }

            if (shakeCount == 0) {
                lastWindowStart = now
            }

            lastShakeTime = now
            shakeCount++

            if (shakeCount >= 3) {
                shakeCount = 0
                onShake3Times()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Preview(showBackground = true)
@Composable
fun SosScreenPreview() {
    WOTO_safetyTheme(darkTheme = false) {
        SosScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun SosScreenDarkPreview() {
    WOTO_safetyTheme(darkTheme = true) {
        SosScreen()
    }
}
