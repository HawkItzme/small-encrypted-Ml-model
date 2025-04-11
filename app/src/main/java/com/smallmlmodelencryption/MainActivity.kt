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
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEEUaCmFwLXNvdXRoLTEiRzBFAiEAqQG0Y%2Ff9D%2F2JZsjpEyxAnlKfliCj1Fhz%2BO4Y27jAV%2BkCIEwbjumnsxQIWi8QWQpTz042tTfDTg5c4M3H7V4BGD18KtQDCL7%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMMTgxODIwNTY4NzQyIgyuCAyzVe4f1sY%2Fp8MqqAMt%2BmmBvMnV%2BGWUnCNf2rQJ%2BEtn7ciRLTSMlrJ4Rfhm%2B0tjdfXK175CWlGvig7qWO8xY2mplm%2BuSaQkhmtZgt0hjQRheoadi8HaGLmq0%2BqbsL8ztYzPFtvNtrPHL9s6OmE0nYDTnX%2F4jPYIQwC4s15fnu1c59XRsrDJVytS1CmlmEoOpJUoBkDSAenPfmhBHrNcxaJAHqVw2kUrmqkEyBK%2Fg8fZpcoxOH1KnlUDgxdylvl8HtC3faPazN3GhyUhpO0FLu3RT5ZtfAJgHqWjXzxiHIGJ56AmXvXeKtYv4861VYpz61avYKIK7lqa31BHKMpb46r6eMusZKBjobIx5BSmbpfPfO2ywkWd7NHHhjzDvnm03%2BuAe9G5L%2Ba3sNuqodRuVI3TuTzMI9LoKF326iPKbu2OFP%2FB5HeajtrOKS%2BeW%2FUYFMlL93AbYoLtikqCysGFTR7W8dAT5K0QWN0vImD1safBELwlmwHa44w7Qg2bA4lyx45ksV3nuU3gDbR1nT6CyhkthbOC4e0hB%2B5tcpqd5lZKsc2f3CU7q83kp0ekPllBC92GOVSrMMWl5L8GOuQCwb09MmyhdlxEa9clWrgOutbt8ETghvNrv%2BryIg1m3cdlbuH7%2FShdGBdcJZfFrN%2FfcpNKwPChUrkrLWphYOaGt20ZDh%2FJXGvvYLlRN8zKOrdgbIiwtDYAw%2BTwPLca31GHZrNDPx%2BW4nSgX9TnRCWiff7VlCFBEHzIBMu3ruc%2BBgly%2Bk%2FpwL7JrCCiSHCGzb6N3nQSaI%2FHC2p1MTBp9Ziid9qjmhvBuEOtooudrVcCQTRbj27M7s7uOzifRXE%2FRMzX%2FPUkCsc6uF8IrTv1jUB97T33VN%2BlNnfaAqUcJg0MfJDq4wCddWcHBbqWVY1yHesMnbnhF%2BRF7MEjHfAx36SH3YzIadmIsFE3jToMaLlSM9yV8yO84JZ4iKem13x3N2lXtB%2Fd7a9uAdBf0BsU60JvQo8KPB0KgiimTgLYgcBkUzXg2%2FH6DyWX2JUa%2BHU0DBqXee8tSjK3oL1mTrHAcus1jWaEqe0%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTH5QHCIAF%2F20250411%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250411T130229Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=41ec838c970ad7d51a1f6359070e09d374b537850f3fd30b44044b81f9835ca0"
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

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEEUaCmFwLXNvdXRoLTEiRzBFAiEAqQG0Y%2Ff9D%2F2JZsjpEyxAnlKfliCj1Fhz%2BO4Y27jAV%2BkCIEwbjumnsxQIWi8QWQpTz042tTfDTg5c4M3H7V4BGD18KtQDCL7%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMMTgxODIwNTY4NzQyIgyuCAyzVe4f1sY%2Fp8MqqAMt%2BmmBvMnV%2BGWUnCNf2rQJ%2BEtn7ciRLTSMlrJ4Rfhm%2B0tjdfXK175CWlGvig7qWO8xY2mplm%2BuSaQkhmtZgt0hjQRheoadi8HaGLmq0%2BqbsL8ztYzPFtvNtrPHL9s6OmE0nYDTnX%2F4jPYIQwC4s15fnu1c59XRsrDJVytS1CmlmEoOpJUoBkDSAenPfmhBHrNcxaJAHqVw2kUrmqkEyBK%2Fg8fZpcoxOH1KnlUDgxdylvl8HtC3faPazN3GhyUhpO0FLu3RT5ZtfAJgHqWjXzxiHIGJ56AmXvXeKtYv4861VYpz61avYKIK7lqa31BHKMpb46r6eMusZKBjobIx5BSmbpfPfO2ywkWd7NHHhjzDvnm03%2BuAe9G5L%2Ba3sNuqodRuVI3TuTzMI9LoKF326iPKbu2OFP%2FB5HeajtrOKS%2BeW%2FUYFMlL93AbYoLtikqCysGFTR7W8dAT5K0QWN0vImD1safBELwlmwHa44w7Qg2bA4lyx45ksV3nuU3gDbR1nT6CyhkthbOC4e0hB%2B5tcpqd5lZKsc2f3CU7q83kp0ekPllBC92GOVSrMMWl5L8GOuQCwb09MmyhdlxEa9clWrgOutbt8ETghvNrv%2BryIg1m3cdlbuH7%2FShdGBdcJZfFrN%2FfcpNKwPChUrkrLWphYOaGt20ZDh%2FJXGvvYLlRN8zKOrdgbIiwtDYAw%2BTwPLca31GHZrNDPx%2BW4nSgX9TnRCWiff7VlCFBEHzIBMu3ruc%2BBgly%2Bk%2FpwL7JrCCiSHCGzb6N3nQSaI%2FHC2p1MTBp9Ziid9qjmhvBuEOtooudrVcCQTRbj27M7s7uOzifRXE%2FRMzX%2FPUkCsc6uF8IrTv1jUB97T33VN%2BlNnfaAqUcJg0MfJDq4wCddWcHBbqWVY1yHesMnbnhF%2BRF7MEjHfAx36SH3YzIadmIsFE3jToMaLlSM9yV8yO84JZ4iKem13x3N2lXtB%2Fd7a9uAdBf0BsU60JvQo8KPB0KgiimTgLYgcBkUzXg2%2FH6DyWX2JUa%2BHU0DBqXee8tSjK3oL1mTrHAcus1jWaEqe0%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTH5QHCIAF%2F20250411%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250411T130321Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=9ddb20b62e4d6a42c204e1f86b13322d8290725169f5a6d2d507e2132f6b6e79"
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
