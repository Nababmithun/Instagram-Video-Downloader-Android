package com.example.instagramvideodownloaderapp

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MainViewModel : ViewModel() {

    fun downloadVideo(context: Context, videoUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(videoUrl)
                val connection = url.openConnection()
                connection.connect()
                val input = BufferedInputStream(url.openStream())

                val fileName = "insta_video_${System.currentTimeMillis()}.mp4"
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                val output = FileOutputStream(file)
                val data = ByteArray(1024)
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download completed: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
