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
                              //  status = "Download complete."
                                val file = File(context.getExternalFilesDir(null), modelFileName)
                                Log.d("DownloadReceiver", "Model downloaded at: ${file.absolutePath}")

                                // 🔐 Offload AES key download to background thread
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEIf%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCmFwLXNvdXRoLTEiRjBEAiB%2BQfForQv9wnDfGaSP0pK6SigCCenjNVfkJyN27Ve0LAIgTF5FYzdpX8GS0Ms4lWCcbE4FBGCt7vkuQG5v%2Bcp401sqywMIEBAAGgwxODE4MjA1Njg3NDIiDJNVyhPszAghyoW2ViqoA9xAajBcjYM%2FRTXv1KebBe2zdYAkFM0SU8p91V3gN7Y0FPw4mzfzocSDYA3g1rYbUlv042k%2B1xr9q3o%2FcRMinBev5KKm42yzXARiKernfBWtw0tvGIBDfNBpWY66%2FL84gM3cK9fr5CRw2b2Fx62sFtTxZkPgXtJbDcLRszhBT66A6tdM40uWE2SqQQSO2VYBBi1MB3XrCJGOgTboClBaRcwkNwe%2Ba4DO9pJpBLRHT8o%2BIldgCBRChBHBPRHASSK3ym%2FpmFU3ka4MxNPsQObG7FuluWcIlhP3g5%2Bg7WeZxc0hZnmKqdch9kqV89wRP8R05QQ5CeNB3OEY3AKqP9rPuNiIBJCGBUM%2FrGFw1w0TvsscdxC9sZfFEt8VxHN5omekBD14grZV9%2BQKCHrRGDvfcjZ%2B0tvhM4KGYo9tsMAxwt7YajUfQ83BxZBi%2F0Z%2ByDMrxijmkIPhxGQ4tIlrDsRZPFwlrgKVvNdQ%2BUUvsbB1ZGl5M0s79mgQ1mCEY3SBbL1v8pTFZS6%2Ba4nAti917e5u3bDcrRZ8ZgrwEnUv%2Bf2HEIn%2F2q92sSDda9wwpuPyvwY65QKo8KkyKZ64tE8YO%2B6UWsGmVdqzX8LfBVm1Kh%2BjPgmUzfbWIXWddO7lxDBlhf%2B%2BcExx1wvp9ztuPvFcyCFRODwQ5ZpZPiqfNXY7unkJ940dneZOPq2nFZbqP3s1iCbUwvfYg2Q3eDSHnVaW6F1ZzOIDzzikVoigpl8w%2B5yUZ6iVZf90vGCeAknvdTgmf%2ByNKpm2%2FlH22NAndBfSr%2Fwq6R5Z%2BHtpBbajOY0TCB4NOET3%2FK62HN594qyqoicJup0D8tTalhFi5LXRsqTGiePaePpNgd6FtuFc45Rz%2FvDkvlMpuYDUEX2AjfEbs0Q1zzo4Pylx95z4iXZb%2B%2FlOAZg%2Bk7zwTZT2KT151MlTEYWOu6vH4ndfhZ2Ab9p3EQrCjpkSNDL1EgcemvSsN2HWzHLDEzrHRjJca1x%2BqYUJa6lvIqFLJMBYeWHWH2KYa1QGfAXft2HZj5%2FqxoHdzvKGxoa8boWY2NM2ktM%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTHQ7MFVNN%2F20250414%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250414T065909Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=b226cd02c0b226565baa9883c8113a56e96c930a9b127935f6886584c9fdc1a3"
                                            Log.d("KeyManager", "Downloading AES key from $aesKeyUrl")
                                        AESKeyManager.downloadAndStoreAESKey(context, aesKeyUrl)
                                        Log.d("KeyManager", "AES key downloaded & stored successfully.")
                                        withContext(Dispatchers.Main) {
                                            status = "Download complete."
                                            onDownloadComplete(file.name)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("KeyManager", "Error downloading/storing AES key", e)

                                        withContext(Dispatchers.Main) {
                                            status = "Download complete — but failed to store AES key."
                                        }
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

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEIf%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCmFwLXNvdXRoLTEiRjBEAiB%2BQfForQv9wnDfGaSP0pK6SigCCenjNVfkJyN27Ve0LAIgTF5FYzdpX8GS0Ms4lWCcbE4FBGCt7vkuQG5v%2Bcp401sqywMIEBAAGgwxODE4MjA1Njg3NDIiDJNVyhPszAghyoW2ViqoA9xAajBcjYM%2FRTXv1KebBe2zdYAkFM0SU8p91V3gN7Y0FPw4mzfzocSDYA3g1rYbUlv042k%2B1xr9q3o%2FcRMinBev5KKm42yzXARiKernfBWtw0tvGIBDfNBpWY66%2FL84gM3cK9fr5CRw2b2Fx62sFtTxZkPgXtJbDcLRszhBT66A6tdM40uWE2SqQQSO2VYBBi1MB3XrCJGOgTboClBaRcwkNwe%2Ba4DO9pJpBLRHT8o%2BIldgCBRChBHBPRHASSK3ym%2FpmFU3ka4MxNPsQObG7FuluWcIlhP3g5%2Bg7WeZxc0hZnmKqdch9kqV89wRP8R05QQ5CeNB3OEY3AKqP9rPuNiIBJCGBUM%2FrGFw1w0TvsscdxC9sZfFEt8VxHN5omekBD14grZV9%2BQKCHrRGDvfcjZ%2B0tvhM4KGYo9tsMAxwt7YajUfQ83BxZBi%2F0Z%2ByDMrxijmkIPhxGQ4tIlrDsRZPFwlrgKVvNdQ%2BUUvsbB1ZGl5M0s79mgQ1mCEY3SBbL1v8pTFZS6%2Ba4nAti917e5u3bDcrRZ8ZgrwEnUv%2Bf2HEIn%2F2q92sSDda9wwpuPyvwY65QKo8KkyKZ64tE8YO%2B6UWsGmVdqzX8LfBVm1Kh%2BjPgmUzfbWIXWddO7lxDBlhf%2B%2BcExx1wvp9ztuPvFcyCFRODwQ5ZpZPiqfNXY7unkJ940dneZOPq2nFZbqP3s1iCbUwvfYg2Q3eDSHnVaW6F1ZzOIDzzikVoigpl8w%2B5yUZ6iVZf90vGCeAknvdTgmf%2ByNKpm2%2FlH22NAndBfSr%2Fwq6R5Z%2BHtpBbajOY0TCB4NOET3%2FK62HN594qyqoicJup0D8tTalhFi5LXRsqTGiePaePpNgd6FtuFc45Rz%2FvDkvlMpuYDUEX2AjfEbs0Q1zzo4Pylx95z4iXZb%2B%2FlOAZg%2Bk7zwTZT2KT151MlTEYWOu6vH4ndfhZ2Ab9p3EQrCjpkSNDL1EgcemvSsN2HWzHLDEzrHRjJca1x%2BqYUJa6lvIqFLJMBYeWHWH2KYa1QGfAXft2HZj5%2FqxoHdzvKGxoa8boWY2NM2ktM%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTHQ7MFVNN%2F20250414%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250414T065947Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=895894f06adf875935191591fe90160ef66923724ec220135318e0e00c49706a"
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
