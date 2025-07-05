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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat


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
            viewModel.downloadMedia(context, url.trim())
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Collecting download status
    LaunchedEffect(viewModel.downloadStatus) {
        viewModel.downloadStatus.collect { message ->
            isLoading = false
            url = ""
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Instagram Media Downloader",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste Instagram URL") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { url = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (url.isNotBlank() && (isPermissionGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                        isLoading = true
                        viewModel.downloadMedia(context, url.trim())
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isBlank()) {
                    Toast.makeText(context, "Please paste a valid Instagram URL.", Toast.LENGTH_SHORT).show()
                } else {
                    if (isPermissionGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        isLoading = true
                        viewModel.downloadMedia(context, url.trim())
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
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                Text("Downloading...")
            } else {
                Text("Download")
            }
        }
    }
}


