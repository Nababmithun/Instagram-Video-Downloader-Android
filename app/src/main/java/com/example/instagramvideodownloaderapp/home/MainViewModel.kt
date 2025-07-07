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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException


class MainViewModel : ViewModel() {

    private val _downloadStatus = MutableSharedFlow<String>()
    val downloadStatus: SharedFlow<String> = _downloadStatus

    private val client = OkHttpClient()

    fun downloadMediaWithSession(context: Context, reelUrl: String, sessionId: String) {
        viewModelScope.launch {
            try {
                val mediaInfo = getReelMediaUrl(reelUrl, sessionId)
                    ?: throw IOException("Media not found or session expired.")

                saveMediaToGallery(context, mediaInfo)

                _downloadStatus.emit("✅ Download complete!")
            } catch (e: Exception) {
                _downloadStatus.emit("❌ Error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun getReelMediaUrl(postUrl: String, sessionId: String): MediaInfo? =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Get media ID
                val embedResp = client.newCall(
                    Request.Builder()
                        .url("https://i.instagram.com/api/v1/oembed/?url=$postUrl")
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                ).execute()

                if (!embedResp.isSuccessful) return@withContext null

                val embedJson = JSONObject(embedResp.body?.string() ?: return@withContext null)
                val mediaId = embedJson.getString("media_id")

                // Step 2: Get media info
                val infoResp = client.newCall(
                    Request.Builder()
                        .url("https://i.instagram.com/api/v1/media/$mediaId/info/")
                        .header("User-Agent", "Instagram 155.0.0.37.107 Android")
                        .header("Cookie", "sessionid=$sessionId")
                        .build()
                ).execute()

                if (!infoResp.isSuccessful) return@withContext null

                val infoJson = JSONObject(infoResp.body?.string() ?: return@withContext null)
                val mediaItem = infoJson.getJSONArray("items").getJSONObject(0)

                return@withContext when {
                    mediaItem.has("video_versions") -> {
                        val url = mediaItem.getJSONArray("video_versions").getJSONObject(0)
                            .getString("url")
                        MediaInfo(url, "video/mp4", "mp4")
                    }

                    mediaItem.has("image_versions2") -> {
                        val url = mediaItem.getJSONObject("image_versions2")
                            .getJSONArray("candidates").getJSONObject(0).getString("url")
                        MediaInfo(url, "image/jpeg", "jpg")
                    }

                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }

    private suspend fun saveMediaToGallery(context: Context, mediaInfo: MediaInfo) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(mediaInfo.url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to download media.")

                val inputStream = response.body?.byteStream()
                    ?: throw IOException("No stream in response.")

                val fileName = "insta_media_${System.currentTimeMillis()}.${mediaInfo.extension}"

                // 🔄 Path & URI based on mimeType
                val (relativePath, contentUri) = if (mediaInfo.mimeType.startsWith("video")) {
                    Environment.DIRECTORY_MOVIES + "/InstagramDownloads" to MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    Environment.DIRECTORY_PICTURES + "/InstagramDownloads" to MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mediaInfo.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(contentUri, contentValues)
                    ?: throw IOException("Failed to insert into MediaStore.")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }

    data class MediaInfo(
        val url: String,
        val mimeType: String,
        val extension: String
    )
}
