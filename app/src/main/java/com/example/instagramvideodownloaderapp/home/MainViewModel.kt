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
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern


class MainViewModel : ViewModel() {

    private val _downloadStatus = MutableSharedFlow<String>()
    val downloadStatus: SharedFlow<String> = _downloadStatus

    fun downloadMedia(context: Context, instagramPostUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(instagramPostUrl)
                    .userAgent("Mozilla/5.0")
                    .get()

                // Try og:video first (rarely works for carousels)
                var mediaUrl = doc.select("meta[property=og:video]").firstOrNull()?.attr("content")
                var isVideo = mediaUrl != null

                if (mediaUrl == null) {
                    val scriptData = extractJsonFromScripts(doc)
                    println("Extracted JSON: $scriptData")
                    mediaUrl = extractFirstVideoUrlFromJson(scriptData)
                    isVideo = mediaUrl != null
                }

                if (mediaUrl == null) {
                    mediaUrl = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
                    isVideo = false
                }

                if (mediaUrl == null) throw IOException("No media URL found in the page.")

                // Start downloading
                val connection = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connect()
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }

                val mimeType = connection.contentType ?: if (isVideo) "video/mp4" else "image/jpeg"
                val inputStream = BufferedInputStream(connection.inputStream)

                val fileName = if (isVideo) {
                    "insta_video_${System.currentTimeMillis()}.mp4"
                } else {
                    "insta_image_${System.currentTimeMillis()}.jpg"
                }

                val relativePath = if (isVideo) {
                    Environment.DIRECTORY_MOVIES + "/InstagramDownloads"
                } else {
                    Environment.DIRECTORY_PICTURES + "/InstagramDownloads"
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, contentValues)
                    ?: throw IOException("Failed to create new MediaStore record.")

                inputStream.use { input ->
                    resolver.openOutputStream(uri)?.use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    } ?: throw IOException("Failed to open output stream.")
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                _downloadStatus.emit("✅ Download complete! Check your gallery.")

            } catch (e: Exception) {
                _downloadStatus.emit("❌ Download failed: ${e.localizedMessage}")
            }
        }
    }

    private fun extractJsonFromScripts(doc: Document): String? {
        val scripts = doc.getElementsByTag("script")
        for (script in scripts) {
            val html = script.html()
            if (html.contains("window.__additionalDataLoaded")) {
                val pattern = Pattern.compile("""window\.__additionalDataLoaded\('.*?',\s*(\{.*\})\);""")
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
            if (html.contains("window._sharedData")) {
                val pattern = Pattern.compile("""window\._sharedData\s*=\s*(\{.*\});""")
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
        }
        return null
    }

    private fun extractFirstVideoUrlFromJson(jsonString: String?): String? {
        if (jsonString == null) return null
        try {
            val root = JSONObject(jsonString)
            val shortcodeMedia = root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
            if (shortcodeMedia != null) {
                // Case 1: Single video
                if (shortcodeMedia.has("video_url")) {
                    return shortcodeMedia.getString("video_url")
                }

                // Case 2: Carousel - loop through edges, find first node with video_url
                val sidecar = shortcodeMedia.optJSONObject("edge_sidecar_to_children")
                if (sidecar != null) {
                    val edges = sidecar.optJSONArray("edges")
                    if (edges != null) {
                        for (i in 0 until edges.length()) {
                            val node = edges.getJSONObject(i).optJSONObject("node")
                            if (node != null && node.has("video_url")) {
                                return node.getString("video_url")
                            }
                        }
                    }
                }
            }

            // Case 3: Alternative structure (older or different post type)
            val items = root.optJSONArray("items")
            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (item.has("video_versions")) {
                        val videoVersions = item.getJSONArray("video_versions")
                        if (videoVersions.length() > 0) {
                            return videoVersions.getJSONObject(0).getString("url")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
