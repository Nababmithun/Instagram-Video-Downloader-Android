package com.example.instagramvideodownloaderapp.home
import android.Manifest
import android.app.Activity
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InstagramDownloaderUI(viewModel = mainViewModel)
        }
    }
}

@Composable
fun InstagramDownloaderUI(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE
    val isPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        requiredPermission
    ) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isLoading = true
            viewModel.downloadVideo(context, url.trim())
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Listen for download result and stop loading
    LaunchedEffect(Unit) {
        viewModel.downloadStatus.collect { message ->
            isLoading = false
            url = "" // Clear the URL after download
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Instagram Video Downloader",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste Instagram Video URL") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { url = "" }) {
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isNotBlank()) {
                    if (isPermissionGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        isLoading = true
                        viewModel.downloadVideo(context, url.trim())
                    } else {
                        launcher.launch(requiredPermission)
                    }
                } else {
                    Toast.makeText(context, "Please paste a video URL.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading // disable while loading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                Text("Downloading...")
            } else {
                Text("Download Video")
            }
        }
    }
}

