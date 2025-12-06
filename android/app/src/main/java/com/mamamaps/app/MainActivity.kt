package com.mamamaps.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await

// --- Models ---
data class MamaReport(
    val id: String,
    val lat: Double,
    val lng: Double,
    val verifiedCount: Int,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MamaMapsTheme {
                MamaMapsApp()
            }
        }
    }
}

@Composable
fun MamaMapsApp() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    
    // --- State ---
    var user by remember { mutableStateOf(auth.currentUser) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var reports by remember { mutableStateOf<List<MamaReport>>(emptyList()) }
    var isCamOpen by remember { mutableStateOf(false) }
    var safetyPopup by remember { mutableStateOf(false) }
    
    // --- Map State ---
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.9716, 77.5946), 15f) // Default BLR
    }

    // --- Permissions ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission Granted, get location
        }
    }

    // --- Effects ---
    
    // 1. Auth & Init
    LaunchedEffect(Unit) {
        if (user == null) {
            try {
                val result = auth.signInAnonymously().await()
                user = result.user
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Request Permissions
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        ))
    }

    // 2. Location Updates (One-shot for demo)
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val token = CancellationTokenSource()
            try {
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentLocation = latLng
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 3. Realtime Reports
    DisposableEffect(Unit) {
        val listener = db.collection("artifacts").document("nammamama-default")
            .collection("public").document("data")
            .collection("reports")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    reports = snapshot.documents.map { doc ->
                        MamaReport(
                            id = doc.id,
                            lat = doc.getDouble("lat") ?: 0.0,
                            lng = doc.getDouble("lng") ?: 0.0,
                            verifiedCount = doc.getLong("verifiedCount")?.toInt() ?: 0,
                            timestamp = doc.getTimestamp("timestamp")?.seconds ?: 0
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    // --- UI ---
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                mapStyleOptions = null // Can load JSON style here
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            reports.forEach { report ->
                val pos = LatLng(report.lat, report.lng)
                // Marker
                Marker(
                    state = MarkerState(position = pos),
                    title = "MAMA SPOTTED",
                    snippet = "Verified: ${report.verifiedCount}",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED)
                )
                // Danger Zone Circle
                Circle(
                    center = pos,
                    radius = 150.0,
                    strokeColor = Color(0xFFEF4444),
                    fillColor = Color(0x55EF4444),
                    strokeWidth = 2f
                )
            }
        }

        // 2. Header UI
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(0xFFFACC15), // Yellow
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Black)
                        Spacer(width = 8)
                        Text("NAMMAMAMA", fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(width = 4)
                        Text("Karthik", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Floating Action Button (Report)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FloatingActionButton(
                onClick = { isCamOpen = true },
                containerColor = Color(0xFFFACC15),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(80.dp)
                    .border(6.dp, Color.Black, CircleShape)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Report", modifier = Modifier.size(32.dp))
            }
        }

        // 4. Camera/Scanning Overlay
        AnimatedVisibility(
            visible = isCamOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ScanningUI(
                onClose = { isCamOpen = false },
                onCapture = {
                    isCamOpen = false
                    safetyPopup = true
                    // Upload Logic Here
                    if (currentLocation != null && user != null) {
                        val reportData = hashMapOf(
                            "lat" to currentLocation!!.latitude,
                            "lng" to currentLocation!!.longitude,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "reporterId" to user!!.uid,
                            "verifiedCount" to 1
                        )
                        db.collection("artifacts").document("nammamama-default")
                            .collection("public").document("data")
                            .collection("reports")
                            .add(reportData)
                    }
                }
            )
        }

        // 5. Safety Popup
        if (safetyPopup) {
            AlertDialog(
                onDismissRequest = { safetyPopup = false },
                containerColor = Color.White,
                icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(48.dp)) },
                title = { Text("VERIFIED!", fontWeight = FontWeight.Black, fontSize = 24.sp) },
                text = { Text("Location Locked & Added to Map.\n+50 Respect", textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                confirmButton = {
                    Button(
                        onClick = { safetyPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Text("Resume Patrol", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
fun ScanningUI(onClose: () -> Unit, onCapture: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fake Camera Preview (In real app use CameraX)
        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
            Text("CAMERA FEED ACTIVE", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center))
        }
        
        // Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha=0.5f), CircleShape)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            // Scanning Graphic
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .border(2.dp, Color(0xFFFACC15), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFFACC15))
                        .align(Alignment.Center)
                )
            }
            
            // Capture Button
            Button(
                onClick = { onCapture() },
                modifier = Modifier.padding(bottom = 48.dp).size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White)
            ) {
                // Inner shutter
            }
        }
    }
}

@Composable
fun Spacer(width: Int = 0, height: Int = 0) {
    Spacer(modifier = Modifier.width(width.dp).height(height.dp))
}

@Composable
fun MamaMapsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFFACC15),
            secondary = Color.Black,
            background = Color(0xFFF3F4F6)
        ),
        typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            )
        ),
        content = content
    )
}