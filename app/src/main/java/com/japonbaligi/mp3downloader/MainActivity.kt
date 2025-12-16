package com.japonbaligi.mp3downloader

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
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
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private var downloadJob: Job? = null
    private var progressJob: Job? = null
    private val downloadingFlag = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeBrowserUI(this)
        }
    }

    override fun onStop() {
        super.onStop()
        downloadJob?.cancel()
        progressJob?.cancel()
        downloadingFlag.set(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        progressJob?.cancel()
        downloadingFlag.set(false)
    }

    @Composable
    fun YouTubeBrowserUI(activity: MainActivity) {
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
                            progress = progress,
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
        activity: MainActivity,
        onStatusUpdate: (String) -> Unit,
        onProgressUpdate: (Float) -> Unit,
        callback: (Boolean, String) -> Unit
    ) {
        downloadJob?.cancel()
        progressJob?.cancel()
        progressJob = null
        downloadingFlag.set(true)

        // Progress polling coroutine
        progressJob = activity.lifecycleScope.launch(Dispatchers.IO) {
            while (downloadingFlag.get() && isActive) {
                try {
                    val py = Python.getInstance()
                    val module: PyObject = py.getModule("downloader")
                    @Suppress("UNCHECKED_CAST")
                    val progObj = module.callAttr("get_progress").toJava(Map::class.java) as Map<String, Any>

                    val downloaded = (progObj["downloaded"] as? Number)?.toLong() ?: 0L
                    val total = (progObj["total"] as? Number)?.toLong() ?: 1L
                    val percent = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f

                    withContext(Dispatchers.Main) {
                        onProgressUpdate(percent)
                    }
                    delay(500)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        break
                    }
                    throw e
                }
            }
        }

        // Download and convert coroutine
        downloadJob = activity.lifecycleScope.launch(Dispatchers.IO) {
            var inputFile: File? = null
            try {
                val py = Python.getInstance()
                val module: PyObject = py.getModule("downloader")

                // Download audio
                withContext(Dispatchers.Main) {
                    onStatusUpdate("İndiriliyor...")
                }
                val downloadedPath = module.callAttr("download_audio", videoUrl, cacheDir.absolutePath).toString()

                inputFile = File(downloadedPath)

                // Use app-private external storage for ffmpeg output
                val musicDir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: throw IllegalStateException("External music directory not available")
                val outputFile = File(musicDir, inputFile.nameWithoutExtension + ".mp3")

                // Convert to MP3
                withContext(Dispatchers.Main) {
                    onStatusUpdate("MP3'e dönüştürülüyor...")
                }
                val cmd = "-y -i \"${inputFile.absolutePath}\" -map_metadata 0 -vn -ar 44100 -ac 2 -b:a 192k \"${outputFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)

                if (session.returnCode.isValueSuccess) {
                    saveToMediaStore(activity, outputFile)
                    withContext(Dispatchers.Main) {
                        callback(true, "Tamamlandı: ${outputFile.name}")
                    }
                } else {
                    val errorMessage =
                        session.failStackTrace
                            ?: session.allLogsAsString
                            ?: "FFmpeg conversion failed"
                    withContext(Dispatchers.Main) {
                        callback(false, errorMessage)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Hata: ${e.message}")
                }
            } finally {
                downloadingFlag.set(false)
                progressJob?.cancel()
                progressJob = null
                inputFile?.delete()
            }
        }
    }

    /**
     * Saves an MP3 file from app-private storage to MediaStore.
     * The file must already exist in app-private storage (getExternalFilesDir).
     *
     * Flow:
     * 1. Insert MediaStore entry with IS_PENDING=1
     * 2. Copy file from app-private storage to MediaStore OutputStream
     * 3. Update MediaStore entry with IS_PENDING=0 to make it visible
     * 4. Delete temporary app-private file on success
     */
    private fun saveToMediaStore(activity: ComponentActivity, mp3File: File) {
        val now = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, mp3File.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/Mp3Downloader"
            )
            put(MediaStore.Audio.Media.DATE_ADDED, now)
            put(MediaStore.Audio.Media.DATE_MODIFIED, now)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = activity.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        try {
            // Copy file from app-private storage to MediaStore
            resolver.openOutputStream(uri)?.use { outStream ->
                FileInputStream(mp3File).use { inStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            } ?: throw IllegalStateException("Failed to open MediaStore OutputStream")

            // Mark as complete - file is now visible in Music library
            val updateValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, updateValues, null, null)

            // Delete temporary app-private file after successful MediaStore export
            mp3File.delete()
        } catch (e: Exception) {
            // Clean up on failure
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
