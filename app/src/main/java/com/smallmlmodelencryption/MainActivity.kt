package com.smallmlmodelencryption

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smallmlmodelencryption.utility.AESKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppContent()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppContent() {
    var modelDownloaded by remember { mutableStateOf(false) }
    var modelFilePath by remember { mutableStateOf<String?>(null) }

    if (!modelDownloaded) {
        DownloadModelScreen_with_DownloadManager(
            onDownloadComplete = { filePath ->
                modelFilePath = filePath
                modelDownloaded = true
            }
        )
    } else {
        modelFilePath?.let { path ->
            ModelLoaderScreen(modelFilePath = path)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DownloadModelScreen_with_DownloadManager(onDownloadComplete: (String) -> Unit) {
    val context = LocalContext.current
    val modelFileName = "encrypted_classification_model.tflite"
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    var status by remember { mutableStateOf("Idle") }
    var downloadId by remember { mutableStateOf<Long?>(null) }

    // Register receiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val receivedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (receivedId == downloadId) {
                    Log.d("DownloadReceiver", "Received broadcast for ID: $receivedId")
                    val query = DownloadManager.Query().setFilterById(receivedId!!)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val statusValue = cursor.getInt(statusIndex)

                        when (statusValue) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                status = "Download complete."
                                val file = File(context.getExternalFilesDir(null), modelFileName)
                                Log.d("DownloadReceiver", "Model downloaded at: ${file.absolutePath}")

                                // 🔐 Offload AES key download to background thread
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjECwaCmFwLXNvdXRoLTEiRzBFAiAFT%2FVDpgEKiyF5np%2By5VJt8KloimhOCaF9RIBLvuYilwIhALuwlHQrMZ6N7tUWagWN2CLTxdlJAjcbcWn3QlW%2F0QGjKtQDCKX%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMMTgxODIwNTY4NzQyIgxVt%2FAfjnlmRfjQyKQqqAMVM1Pwt7m3grrwN5JtWnaK5ta3ikJ%2Fmr4BkDYDwWBtOuvDDpQKDLU06qwSII5%2FennFUvw1kHyxul0f5iLyMx01qkCc2iPRH%2F9vvOkeadkYgmzSHAL13pDij2IIChBIMgJItal1xcitnGa3vEl445He6Ugov6z2fRLiFkyveZDbWhxiDOznXxIMk4PHxAOE5UPbJmYTcir2GUXAOveQrH2tsoIxxWHvIAjgpUZNiivrfoJmmvp0fB7gFPAmbIrYqtoAN6S0ziiQ7SifqmP19JOHiDng9e5JoBfJKxFlOc5Q5YGBjCmwmQUuL9j9Dnra8BT6ecdcRxXHekQWjAT%2B%2FTvKllczJ5iFfCa7nj3QQE8yOFIHPLQoqD5q4xd22wIC%2BtOnC9l%2BSMSWX0mlzvGl81NxR7M9bj8LlWVBKa6o6uoaOy70Hplzlm91vnaZzaJbqaMK0czoAz%2B6IfDDDkNovlV0UUFOBBX%2FPiCbMSDTxEOmxQF1Un04EouhPq46eIY7iNb6KdxPktyADZOq3TrO2UY89IcmSnl1ZkEo6jZd3jU3Rjq2HTobq6Q%2FMJjm3r8GOuQCGQaOkCKuJv6Ibzltkm7Y2rWe3XN2nXLv7d%2FT2Pfp%2FO4wNsB%2B47aNGY9xJthmlkHUAphf574C%2Fs46EsVg3OApZOE6qfT3wNTI8f42N5WBH6nJsdQdPN20PqPXTkceAqeIv1doLiznVOrM7rUGcfNo2OE9JwEvwJ1hxcjCNrq4smluHdJOfIaw9V5NYEe3urU1Xr%2FYl9TIrhf1%2BFWVxZCOSieTLNDO9GVuvxSOYnqHkje4Gm5M0pvGuci6dqmhulL6tp%2B%2FPT%2B27%2Bo8KEUCPMbD6nFNF2nAOcfuAxd%2FnYJnmkGZXM7lPqwt%2BpYpeDlXf57sEKZaYqgAi69j2x522kCAFQQ5CDb9jvt5dneueR4qGKIQth%2B5XDS5cJmmvTU%2B9eMwqOkfAEQG0JVx6CBXcpGK7BLHxOpwjtz0bo%2FAR834SFN4ANiR7higT95ZdV6DhmGw0PWA2K9G8%2B1tQ1AyKSuqgORqcPQ%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTENFG5DKL%2F20250410%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250410T121404Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=4e2909c3992f1b902d8039d8369a0f8fe608118eccb8d931b07587506a990eae"
                                            Log.d("KeyManager", "Downloading AES key from $aesKeyUrl")
                                        AESKeyManager.downloadAndStoreAESKey(context, aesKeyUrl)
                                        Log.d("KeyManager", "AES key downloaded & stored successfully.")
                                        withContext(Dispatchers.Main) {
                                            onDownloadComplete(file.name)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("KeyManager", "Error downloading/storing AES key", e)
                                    }
                                }
                            }

                            DownloadManager.STATUS_FAILED -> {
                                status = "Download failed."
                                Log.e("DownloadReceiver", "Download failed for ID: $receivedId")
                            }

                            DownloadManager.STATUS_PAUSED -> {
                                status = "Download paused."
                                Log.w("DownloadReceiver", "Download paused")
                            }

                            DownloadManager.STATUS_PENDING -> {
                                status = "Download pending..."
                                Log.d("DownloadReceiver", "Download pending")
                            }

                            DownloadManager.STATUS_RUNNING -> {
                                status = "Downloading..."
                                Log.d("DownloadReceiver", "Download in progress...")
                            }
                        }
                    } else {
                        Log.w("DownloadReceiver", "Cursor is empty or null")
                        status = "Error: Couldn't read download status"
                    }
                    cursor?.close()
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Status: $status")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            status = "Downloading..."
            Log.d("DownloadManager", "Starting download of encrypted model")

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjECwaCmFwLXNvdXRoLTEiRjBEAiAgN8TN3zh2N%2BTJbxByNPCQZ%2FYA1Am4aKyHHchrfSxQBQIgazClztJ8bCze32zR5DU92KG31dpTmYxVQtPm2aEwk68q1AMIpf%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FARAAGgwxODE4MjA1Njg3NDIiDPMkbsXDYb7Yt1mrgCqoAwoSOYy7m7qWJvhhp3NezCZMZYQgNrc68XmxlZeMz19Mwn%2BkMdUccs57YaDCDLZUz4v6CK83FeqsIHuk6dXNTdObE6lYjsEWc5wXYnAJceK3CspvFvGBagIO6jd89rX0Oy3f4oZrcl1cMZ%2FdJ8kpDhXzb5IcxJkafMrgHwDhZB98gTjV6qbpDbjGYVz9vHoFEK9ztTyaV0uR0ojFzI40uwN1K%2B5RYOcneusUW6S43rIHQZISRRZ8GdD1%2ByIrQq9F2SQVk50qRXE71GU8P4SGbz9gLn9fjgnlA2M7bQ%2Bl5auKsPqQLOKVwYXzBnjmR%2F87%2Fl6O9aUw%2B0sNkeRO0HgVFaaHnSHPv5IT2jxHB4XMrxTLj4z4U7zkRK%2BF%2BS%2BYEHAxxJ45Jw0mBFcz4zmYHp2rUnv21KCr35ARS75ZBMgycLF%2F3AHstH6ey%2Bl6487%2B8q3t%2BubsVSXS0DOAsNxFatwzXBQcyrmPwstZbeY0epppfR9vnhBBvZhdR3WeDuNBPYU1TLm%2FvyBclwIHLxsxCirbR4BoydsAtWdLekA%2FpE7jHGz8z4QpbGtn%2FuswmObevwY65QKN15ZEiiXAzM49ZtjNh4rM%2B%2Fj09gY6Nc93zcm3mYKbWvU%2Fx9aaxOjSfTs3qDNQPfFsXrYsOlbCAd2HT3DjWu1aUwRyP%2B1Pc54iVskX2Sm3M9FFZpXg6eEWz0K9WHin3E8KDQ3F6QCeERm%2B%2BIFtavIYjMWUxscsbUvskKlb0LQsAw2ErJ1cCvP2JHDB4JvoiNGh02GCtDH9WaVJ0ViIvcsbJSmefYY2Owd6aD3M%2FQIJtpoZk9X36PGnjGk5x7MhCNxqqdI1ZzwPsLoStqDSCsG2CKUMYPujhMbldIT%2BPn1XCcRiB%2F28fCbUu4haDc2wcT0kYJ%2FSbrdF1MNZAR7DBtqpQr0wjoi6O3EfGkvDwukPYaPq755SR7Qs2odW%2FXchCH1OULlKookKTyjGibVtA1sWArAVtCPvCdcQ5NpJzxDKyRQhnssuj8F1h4GAuVAdIXRe9BwaUZJr3fUQT12YBvj2k38l6wo%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTANYEGY5J%2F20250410%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250410T121444Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=e082117288d7ba9d3d95a4088b40d4a9304a0a119614df58118938a8848f75fc"
                val request = DownloadManager.Request(Uri.parse(presignedUrl))
                .setTitle("Downloading Model")
                .setDestinationInExternalFilesDir(context, null, modelFileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            downloadId = downloadManager.enqueue(request)
            Log.d("DownloadManager", "Download started with ID: $downloadId")
        }) {
            Text("Download Encrypted Model")
        }
    }
}

@Composable
fun ModelLoaderScreen(
    modelFilePath: String,
    viewModel: ModelLoaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val status = viewModel.status
    val inferenceResult = viewModel.inferenceResult

    var triggerLoad by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Status: $status")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { triggerLoad = true }) {
            Text("Load & Run Inference")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (inferenceResult.isNotEmpty()) {
            Text(text = "Inference Result: $inferenceResult")
        }
    }

    LaunchedEffect(triggerLoad) {
        if (triggerLoad) {
            triggerLoad = false
            viewModel.runModelLoader(context, modelFilePath)
        }
    }
}
