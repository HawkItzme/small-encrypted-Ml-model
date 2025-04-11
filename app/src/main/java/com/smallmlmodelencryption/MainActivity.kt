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
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEE4aCmFwLXNvdXRoLTEiSDBGAiEApS5TgK6%2FuzAplPJ7qGuD2j%2B0PR6LLJKneiKLPhPXalsCIQDoQT3YMFsVC3yfGHl4zJsBnF6W8YG2JSmiXi%2BNnjKhBCrUAwjH%2F%2F%2F%2F%2F%2F%2F%2F%2F%2F8BEAAaDDE4MTgyMDU2ODc0MiIMvd1LK9ZZuQEcoM%2BRKqgDCeTpd4Oa8qQsQF7ekPElfb6%2Bg39dRs4qTq6i5Y%2Fv%2FPvL32hwcXrK%2FcoK2nHBkE9VaDhUdxAjDUVgR1WLNsYFHl9ue2sQZa7SEmPOxP9LOaaFrR6ZPdCfaWiNAthIpBOh4hUJd2ByTT4BEbNQpaA%2BEJmMDZyxoGItHViCB%2Bfu6yO%2BLIhR5d0DRumQ30sDxS8OSM8KuIVHufYDvj00jnL7dh6E1ucV9BK%2FDJMfdxyjslabRfP9c8ltYhCLSGpQuYUf%2BZoFJXg7HbQgPF1a3KllpIo9u2JIcfujg%2FWhDHGI3WynjW8zeSGOS89E87JQv601W2Nj7NF9f%2FGQ5L7oopvFbGpZZB0MA99kUqDBG4S7ZOSxEDTARwZBis7czzTBKq2BHB6F7rVi233nEynxv8oqUHUm3ljA5xEGAc3nWL3cZtkaMTLDvyTmBKJLLTakfHL%2F1qh1gDlHOgP5JA%2FurNerviYI7XTdLQV9u8cQ1Ydir8cD3DWzyY%2BUnoTnV3TLohaJ9r0eFOrVsiq8Yltl0TENKcwe87A3%2BIJ2xaK0Z4icVlSGlUh6RXeE9zDFpeS%2FBjrjAvnV0Y9WCZIr%2BkicXY1VyXcrW54qtApeS4pb8mffd5x5Go6I84m81o4ta%2BD7amzblshkchT5D56l6ifWb%2FLFpnx968vssOGsYPZQv5k41zHgK%2FpYypbzyflNfoz5ncLi7j8zpyMDhDlsNV8ieWqvICzIz%2FKbdc3XecDi3ak%2FaHQ8drGwt1f5NjwuqNdj7Fn3K3UVCkOh76oE9GWChO1QB4T12Au0d%2FqF3MX8ZZ6SEUBrRPUrB2FHv6hxNwpXfgESGuPyIyJW8wrcdS3XgPAUxY3TowKsmN%2FDgcUJf354dozhJRCbjSXH4A2ELq256eTwS7miNo3D4of3Qtx5wDPPsBUlOhbBZg1XqxmHNb8z%2BgxkPIlAq%2FPxc4im4XFhZxbAAevICAlgtenInNU1s7w5YBqnX%2FqsYVfGFL3pg6YFfJYrhdXztuPMsU4EK1BxrPhSNYkhvIPg1BohZlu2aqZfugAGcqQ%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTFLGH24GC%2F20250411%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250411T215409Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=8de1a83ff31d4909ec1aecb160391020fd723da5fb116a1741586085d8160b7c"
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

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEE4aCmFwLXNvdXRoLTEiSDBGAiEApS5TgK6%2FuzAplPJ7qGuD2j%2B0PR6LLJKneiKLPhPXalsCIQDoQT3YMFsVC3yfGHl4zJsBnF6W8YG2JSmiXi%2BNnjKhBCrUAwjH%2F%2F%2F%2F%2F%2F%2F%2F%2F%2F8BEAAaDDE4MTgyMDU2ODc0MiIMvd1LK9ZZuQEcoM%2BRKqgDCeTpd4Oa8qQsQF7ekPElfb6%2Bg39dRs4qTq6i5Y%2Fv%2FPvL32hwcXrK%2FcoK2nHBkE9VaDhUdxAjDUVgR1WLNsYFHl9ue2sQZa7SEmPOxP9LOaaFrR6ZPdCfaWiNAthIpBOh4hUJd2ByTT4BEbNQpaA%2BEJmMDZyxoGItHViCB%2Bfu6yO%2BLIhR5d0DRumQ30sDxS8OSM8KuIVHufYDvj00jnL7dh6E1ucV9BK%2FDJMfdxyjslabRfP9c8ltYhCLSGpQuYUf%2BZoFJXg7HbQgPF1a3KllpIo9u2JIcfujg%2FWhDHGI3WynjW8zeSGOS89E87JQv601W2Nj7NF9f%2FGQ5L7oopvFbGpZZB0MA99kUqDBG4S7ZOSxEDTARwZBis7czzTBKq2BHB6F7rVi233nEynxv8oqUHUm3ljA5xEGAc3nWL3cZtkaMTLDvyTmBKJLLTakfHL%2F1qh1gDlHOgP5JA%2FurNerviYI7XTdLQV9u8cQ1Ydir8cD3DWzyY%2BUnoTnV3TLohaJ9r0eFOrVsiq8Yltl0TENKcwe87A3%2BIJ2xaK0Z4icVlSGlUh6RXeE9zDFpeS%2FBjrjAvnV0Y9WCZIr%2BkicXY1VyXcrW54qtApeS4pb8mffd5x5Go6I84m81o4ta%2BD7amzblshkchT5D56l6ifWb%2FLFpnx968vssOGsYPZQv5k41zHgK%2FpYypbzyflNfoz5ncLi7j8zpyMDhDlsNV8ieWqvICzIz%2FKbdc3XecDi3ak%2FaHQ8drGwt1f5NjwuqNdj7Fn3K3UVCkOh76oE9GWChO1QB4T12Au0d%2FqF3MX8ZZ6SEUBrRPUrB2FHv6hxNwpXfgESGuPyIyJW8wrcdS3XgPAUxY3TowKsmN%2FDgcUJf354dozhJRCbjSXH4A2ELq256eTwS7miNo3D4of3Qtx5wDPPsBUlOhbBZg1XqxmHNb8z%2BgxkPIlAq%2FPxc4im4XFhZxbAAevICAlgtenInNU1s7w5YBqnX%2FqsYVfGFL3pg6YFfJYrhdXztuPMsU4EK1BxrPhSNYkhvIPg1BohZlu2aqZfugAGcqQ%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTFLGH24GC%2F20250411%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250411T215450Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=378911b706779112f650b57cae192ec70165e5268dc124157a651f9c10f1c478"
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
