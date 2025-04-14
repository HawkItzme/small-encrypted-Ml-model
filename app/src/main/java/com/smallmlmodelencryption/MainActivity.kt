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
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEI3%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCmFwLXNvdXRoLTEiRzBFAiEA0aTSd4rJ0CPr5xaGcW3dSPnLi5ODncYIgf07eIFvPscCIBA3Kzoz8CFUsbcy61rrglsGYxyxkh4KCzoPh4Lad3BTKssDCBYQABoMMTgxODIwNTY4NzQyIgwRPdnoGhQVxEIc9cEqqAM%2FKj%2Fm5un12QA3Tl26MRcd8EIWQKw1A3BQHn1QF4bCYUBLSpzxBRMbVCOLGzshhgmtwJtGp9m9oBtu2Eyiiq1RRRZXSePVlCmcOgicbMn8KVt34TMoCD%2FFcv452lqDoEOVvKQi9U2YuvYEWsECHo7yqjHVUTpBECQBzN09zWMaIUA1eUBDUuJuSOzxMUiipqFdvlwXbW0KsYyAhGwA58B2YVnc80XO3ARg9KFSUDOgMVD03ByqKKpE%2FtBGjm8F97QZEKJuU6Rg0hTGs3nO4Ay1rfGgaebvsnxA%2BzS2nnl%2FWX0NNXEopYiH4ei%2FX2PZMemZU%2FQSPiF98daKOnM%2FWPpJD94Evx11%2FUkxr6fR93%2F4Xc0wZ%2FErai1UyKX7GiK5Vk%2BGaRYAo58DRxgVPto5FTuu%2FR2G2MqCoEr%2Bib%2FVLpR4UpNSCXjZYbq4yxj2zU58EHd6UnWxy%2BJSHE1m3tHiJM0RbT3FRwWMClKDVBi88T%2BbkASeXlkkQH7UpMTMHmMLKCmFSAu31QVc6aSp3fqgf3d4fjl3AN6iF9%2FI31gu41qbEfJugGtsUVW5MKbj8r8GOuQCRBJAup15mfm5TonH0CxM2U3XMjxF6aQ%2FgV2IkLaDAqO5XYIynBG4BFF0tpT94JBmnJhoFZPSq6225Xhu%2FQgEs9LjyBA6OBOErMG9LKJ%2FY5%2FQgk%2B3BK3HhsDDiT%2BZ6tuiUMGTaJSevED8xRKXMCa2geMalsdVwQ9jk2w4KSxKI7gwuIoUdfjp3fTnrhd6e1ANR%2F8htxCJhqvxeqhYR5VUKEarsgFHy4sBanqKvAWxgt1pYl8eeNzokSYS%2FzU%2Ftjfgb2ObwoDzFgxFeXUOBMA1tov7HtDtBRhEHEUObc29KUTKUK%2BZuajYjmq7e%2FCMqGG%2Fy3xssxwp8t4SV4bGU3XsJkvti4VyUaN2fNQeKpV9Ulj%2B3y0X3ZTjkglobkJ6Hd3sFHzKGUVHK8kspQMyEM2nRG5FnAfg4zJ900da%2FnydLCc64ptJcGPad1TB3eoxwf4jtGSh9wl3j9WsjaLt4b%2BIVdUx0PI%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTOSULP5KO%2F20250414%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250414T125221Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=3d06dda4894abe54120f99e2ef3fe469b5f84bfabc225df242f65d8f8519c74f"
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

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEI3%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCmFwLXNvdXRoLTEiRzBFAiEA0aTSd4rJ0CPr5xaGcW3dSPnLi5ODncYIgf07eIFvPscCIBA3Kzoz8CFUsbcy61rrglsGYxyxkh4KCzoPh4Lad3BTKssDCBYQABoMMTgxODIwNTY4NzQyIgwRPdnoGhQVxEIc9cEqqAM%2FKj%2Fm5un12QA3Tl26MRcd8EIWQKw1A3BQHn1QF4bCYUBLSpzxBRMbVCOLGzshhgmtwJtGp9m9oBtu2Eyiiq1RRRZXSePVlCmcOgicbMn8KVt34TMoCD%2FFcv452lqDoEOVvKQi9U2YuvYEWsECHo7yqjHVUTpBECQBzN09zWMaIUA1eUBDUuJuSOzxMUiipqFdvlwXbW0KsYyAhGwA58B2YVnc80XO3ARg9KFSUDOgMVD03ByqKKpE%2FtBGjm8F97QZEKJuU6Rg0hTGs3nO4Ay1rfGgaebvsnxA%2BzS2nnl%2FWX0NNXEopYiH4ei%2FX2PZMemZU%2FQSPiF98daKOnM%2FWPpJD94Evx11%2FUkxr6fR93%2F4Xc0wZ%2FErai1UyKX7GiK5Vk%2BGaRYAo58DRxgVPto5FTuu%2FR2G2MqCoEr%2Bib%2FVLpR4UpNSCXjZYbq4yxj2zU58EHd6UnWxy%2BJSHE1m3tHiJM0RbT3FRwWMClKDVBi88T%2BbkASeXlkkQH7UpMTMHmMLKCmFSAu31QVc6aSp3fqgf3d4fjl3AN6iF9%2FI31gu41qbEfJugGtsUVW5MKbj8r8GOuQCRBJAup15mfm5TonH0CxM2U3XMjxF6aQ%2FgV2IkLaDAqO5XYIynBG4BFF0tpT94JBmnJhoFZPSq6225Xhu%2FQgEs9LjyBA6OBOErMG9LKJ%2FY5%2FQgk%2B3BK3HhsDDiT%2BZ6tuiUMGTaJSevED8xRKXMCa2geMalsdVwQ9jk2w4KSxKI7gwuIoUdfjp3fTnrhd6e1ANR%2F8htxCJhqvxeqhYR5VUKEarsgFHy4sBanqKvAWxgt1pYl8eeNzokSYS%2FzU%2Ftjfgb2ObwoDzFgxFeXUOBMA1tov7HtDtBRhEHEUObc29KUTKUK%2BZuajYjmq7e%2FCMqGG%2Fy3xssxwp8t4SV4bGU3XsJkvti4VyUaN2fNQeKpV9Ulj%2B3y0X3ZTjkglobkJ6Hd3sFHzKGUVHK8kspQMyEM2nRG5FnAfg4zJ900da%2FnydLCc64ptJcGPad1TB3eoxwf4jtGSh9wl3j9WsjaLt4b%2BIVdUx0PI%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTOSULP5KO%2F20250414%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250414T125328Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=67d51e56512d722221823677626d71ccd6b618c95de48dd5169624b9f91d0271"
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
