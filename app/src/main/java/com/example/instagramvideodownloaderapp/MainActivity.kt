package com.example.instagramvideodownloaderapp

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InstagramDownloaderApp() }
    }
}

@Composable
fun InstagramDownloaderApp() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Idle") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Instagram URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    status = "Fetching video URL..."
                    try {
                        val videoUrl = fetchInstagramVideoUrl(url.trim())
                        status = "Downloading..."
                        val success = downloadVideoToGallery(context, videoUrl)
                        status = if (success) "Download complete ✅" else "Download failed ❌"
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank()
        ) {
            Text("Download Video")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(status)
    }
}

suspend fun fetchInstagramVideoUrl(postUrl: String): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url(postUrl)
        .header("User-Agent", "Mozilla/5.0")
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) throw Exception("Network error: ${response.code}")

    val html = response.body?.string() ?: throw Exception("Empty HTML response")
    val regex = Regex("""<meta property="og:video" content="([^"]+)""")
    val match = regex.find(html) ?: throw Exception("Video URL not found in HTML")

    return@withContext match.groupValues[1]
}

suspend fun downloadVideoToGallery(context: android.content.Context, videoUrl: String): Boolean = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder().url(videoUrl).build()
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) return@withContext false
    val inputStream: InputStream = response.body?.byteStream() ?: return@withContext false

    val filename = "insta_${System.currentTimeMillis()}.mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Instagram")
        }
    }

    val resolver = context.contentResolver
    val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val videoUri: Uri? = resolver.insert(videoCollection, contentValues)

    videoUri?.let { uri ->
        resolver.openOutputStream(uri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return@withContext true
    } ?: return@withContext false
}