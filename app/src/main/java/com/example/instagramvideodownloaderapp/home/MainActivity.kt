package com.example.instagramvideodownloaderapp.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.instagramvideodownloaderapp.utils.SessionManager


class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InstagramDownloaderUI(viewModel)
        }
    }
}

@Composable
fun InstagramDownloaderUI(viewModel: MainViewModel) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var sessionId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE
    val isPermissionGranted = remember {
        ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isLoading = true
            viewModel.downloadMediaWithSession(context, url.trim(), sessionId.trim())
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Load saved sessionId from DataStore
    LaunchedEffect(Unit) {
        SessionManager.sessionIdFlow(context).collect { savedSessionId ->
            sessionId = savedSessionId
        }
    }

    // Save sessionId when it's changed
    LaunchedEffect(sessionId) {
        SessionManager.saveSessionId(context, sessionId)
    }

    // Observe download status
    LaunchedEffect(Unit) {
        viewModel.downloadStatus.collect { message ->
            isLoading = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Instagram Reel Downloader", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste Reel URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = sessionId,
            onValueChange = { sessionId = it },
            label = { Text("Paste Session ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isBlank() || sessionId.isBlank()) {
                    Toast.makeText(context, "URL and Session ID required", Toast.LENGTH_SHORT).show()
                } else {
                    if (isPermissionGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        isLoading = true
                        viewModel.downloadMediaWithSession(context, url.trim(), sessionId.trim())
                    } else {
                        launcher.launch(requiredPermission)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Downloading...")
            } else {
                Text("Download Reel")
            }
        }
    }
}
