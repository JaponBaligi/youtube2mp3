package com.japonbaligi.mp3downloader

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.arthenica.ffmpegkit.FFmpegKit
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeBrowserUI(this)
        }
    }

    @Composable
    fun YouTubeBrowserUI(activity: ComponentActivity) {
        var currentUrl by remember { mutableStateOf<String?>(null) }
        var isVideoPage by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("") }
        var progress by remember { mutableStateOf(0f) }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                activity.runOnUiThread {
                                    currentUrl = url
                                    isVideoPage = YouTubeUrlHelper.isVideoPage(url)
                                }
                            }
                        }
                        
                        loadUrl("https://m.youtube.com")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // FloatingActionButton overlay - only visible on video pages
            if (isVideoPage) {
                FloatingActionButton(
                    onClick = {
                        val normalizedUrl = YouTubeUrlHelper.normalizeVideoUrl(currentUrl)
                        if (normalizedUrl != null && !isDownloading) {
                            isDownloading = true
                            status = "İndiriliyor..."
                            progress = 0f
                            downloadAndConvertToMp3(
                                normalizedUrl, 
                                activity,
                                { newStatus -> status = newStatus },
                                { newProgress -> progress = newProgress },
                                { success, message ->
                                    isDownloading = false
                                    status = message
                                    if (success) {
                                        progress = 1f
                                    } else {
                                        progress = 0f
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("↓")
                    }
                }
            }

            // Status and progress indicator
            if (status.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    if (progress > 0f && progress < 1f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    private fun downloadAndConvertToMp3(
        videoUrl: String, 
        activity: ComponentActivity, 
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Float) -> Unit,
        callback: (Boolean, String) -> Unit
    ) {
        var isDownloading = true
        
        // Progress tracking thread
        Thread {
            while (isDownloading) {
                try {
                    val py = Python.getInstance()
                    val module: PyObject = py.getModule("downloader")
                    @Suppress("UNCHECKED_CAST")
                    val progObj = module.callAttr("get_progress").toJava(Map::class.java) as Map<String, Any>

                    val downloaded = (progObj["downloaded"] as? Number)?.toLong() ?: 0L
                    val total = (progObj["total"] as? Number)?.toLong() ?: 1L
                    val percent = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f

                    activity.runOnUiThread { onProgressUpdate(percent) }
                    Thread.sleep(500)
                } catch (_: Exception) {
                    break
                }
            }
        }.start()

        // Download and convert thread
        Thread {
            try {
                val py = Python.getInstance()
                val module: PyObject = py.getModule("downloader")

                // Download audio
                activity.runOnUiThread { onStatusUpdate("İndiriliyor...") }
                val downloadedPath = module.callAttr("download_audio", videoUrl, cacheDir.absolutePath).toString()

                val inputFile = File(downloadedPath)
                val outputFile = File(cacheDir, inputFile.nameWithoutExtension + ".mp3")

                // Convert to MP3
                activity.runOnUiThread { onStatusUpdate("MP3'e dönüştürülüyor...") }
                val cmd = "-i \"${inputFile.absolutePath}\" -vn -ar 44100 -ac 2 -b:a 192k \"${outputFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)

                isDownloading = false

                if (session.returnCode.isValueSuccess) {
                    saveToDownloads(outputFile)
                    callback(true, "Tamamlandı: ${outputFile.name}")
                } else {
                    callback(false, "Dönüştürme hatası!")
                }
            } catch (e: Exception) {
                isDownloading = false
                callback(false, "Hata: ${e.message}")
            }
        }.start()
    }

    private fun saveToDownloads(outputFile: File) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, outputFile.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Mp3Downloader")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { outStream ->
                FileInputStream(outputFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }
    }
}
