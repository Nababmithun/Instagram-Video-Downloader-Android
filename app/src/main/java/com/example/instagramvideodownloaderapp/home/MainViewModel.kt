package com.example.instagramvideodownloaderapp.home

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {

    private val _downloadStatus = MutableSharedFlow<String>()
    val downloadStatus: SharedFlow<String> = _downloadStatus

    fun downloadVideo(context: Context, instagramPostUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Extract the actual video (.mp4) URL using Jsoup
                val doc = Jsoup.connect(instagramPostUrl)
                    .userAgent("Mozilla/5.0") // Important to prevent 403
                    .get()

                val videoUrl = doc.select("meta[property=og:video]")
                    .firstOrNull()?.attr("content")
                    ?: throw IOException("Couldn't extract video URL from the page.")

                // Step 2: Start downloading the video
                val connection = URL(videoUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val mimeType = connection.contentType
                if (!mimeType.startsWith("video")) {
                    throw IOException("Invalid content type: $mimeType")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }

                val input = BufferedInputStream(connection.inputStream)
                val fileName = "insta_video_${System.currentTimeMillis()}.mp4"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/InstagramDownloads"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val videoUri =
                    resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (videoUri != null) {
                    input.use { inputStream ->
                        resolver.openOutputStream(videoUri)?.use { outputStream ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.flush()
                        } ?: throw IOException("Failed to open output stream.")
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(videoUri, contentValues, null, null)

                    _downloadStatus.emit("✅ Download complete! Check your gallery.")
                } else {
                    throw IOException("Failed to create MediaStore record.")
                }

            } catch (e: Exception) {
                _downloadStatus.emit("❌ Download failed: ${e.message}")
            }
        }
    }
}