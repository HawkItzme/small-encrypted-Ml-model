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
    //  val modelFileName = "big_encrypted_model.tflite"
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
                                        val aesKeyUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/classification_model_aes_key.txt?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEBQaCmFwLXNvdXRoLTEiRjBEAiAqbUIeNnqKm3vb9%2FfzJKs3fxL4z0zLikxt8PNixKsM9QIgB5vGRNa%2FlcYCZH0ZMjHuYrUMxfF57mg5vbKyV2%2BnFSgq1AMIjf%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FARAAGgwxODE4MjA1Njg3NDIiDM1HtfyM5%2Fqv0UJKSiqoA%2BU1KqvGJD%2BOecIUwDPBq5xY8WVVFe7%2FylAK5iDy6Y49SBXXPIcqaC3sJuFv412A70GH1MJc3b1ixXxOXkkoMQa3dqWdsAXISdbkcdNNlv%2BufNWcYdpuMhfDCmtvOfMtPsodeKbG%2FZx0GqJ7v%2F4ulmGS6VFSX7%2FXmlhPw%2B942R3yGkf0pDDACJi9ISM3ig8E%2BtSJM6fc6d9Cuc9nV5Ai8Tz0tN3%2FwFDJHNu%2BWsNChCWKx%2B02KJwbTjCtP%2B8RWv3GDeXqZ1gypgfm6TQVZOC%2FgoUG600wQ18lj1974pVBDG0EldNdyVTBToUE3W3VNzqgmNZptU306QDBlmd7oERQCxaye3o5aZ5UEPG1xIgTzczLH1GLHkgE3p9KO2vcjWDl5NwvQpyiXZw1NE0sA1XRS6HHtBCASRLNQbX593GyWbhsOXgOS5AORhJmv%2BANYHaYUQ6K5ECWLhx4jwRsPGt3yNQxz%2FLULTyEsRNzQA%2B2IC5N8sVWOMCGOUAEKf8YalbJaHU9xFfNcXewWhMCOqcGgm%2BNue3ldyx4xw%2F4KJ3DLQNTTKBrGPn1Yusw6%2BDXvwY65QLu3gFUz8cv%2Bb2wdCCw1IEnL1PqeFvN02xLtDnKREPPq7cEUIfztbjX0O4v5FWdBlrNWgunYUoqnu8SrAPhoF5LFt5NBXKlsWS%2FgLgGkTvToP2mxyYSH%2Ba577VEKbeoncYJSQtoorGeRmCZ1m05KwDijXSsqPfO2x2knTj0HscA81qB0vuiSCW5WQ59SLe%2Fyervy5Ei7e4cfC4XfOmSNhwv%2BQyyGSEz7e8ic%2F4ieMnFfgXwCieykIfnnU%2BDdGyEF4tpEU1n0waknd2Kj8iaXMTTy%2Bd1%2BSVTdClN0XsGtdYkPnph3R7gZL6hqxbQ1hbj6OzgSpynZdG2VrVRpZG%2FA72iUYUSZejrKEkOjNOssqpVMg8VJh%2FSIlTwBD09kqP%2BIucBDL4jWjFc0kSZma6G%2BRApJZT3AOD1uBr7BySIFBlpTOB5L1lpgOpx5GqnxQhzT3ugg6NPK%2BNdAAcrTt07DwEWeiOn5yM%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTFEQ3B5F2%2F20250409%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250409T121819Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=d1690de2d409f7496be2987b6e39ddbfe19358d2f1bade7eb5262deb6bf9ba76"
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
            status = "Starting download..."
            Log.d("DownloadManager", "Starting download of encrypted model")

            val presignedUrl = "https://s3.ap-south-1.amazonaws.com/externally-encrypted-classification-model.tflite/encrypted_classification_model.tflite?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEBIaCmFwLXNvdXRoLTEiRzBFAiBxaeKfrAB1wVlSMdxWqQ1F3j9AgW%2F9SOmGDkvNndsIhwIhAKooT9BC50sf3EwD6dHriDyusUuTJoaVH2OqrA5PGqNkKtQDCIv%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMMTgxODIwNTY4NzQyIgwga0KRbfBqTLGGn3kqqAM0wfOL0pTRmkBTY5Z%2BIgOyib3%2BCDJBff9GQcbRFdhqOmH1ab6xFjHXDLRO4z64pJFigd0SiBF1%2F9pQ089yzX4u93Q48b%2FLPhX6FMsxwgGbZJVeLLF%2FN74NiAQAxxB6yPZu555DKatkMvCecG0N0TS1FK0EVzViG35KS8miunmbzNiM6ax1OB9mBG1yWSdwu3yNyCT3Q0iyax6Dn3rcn%2B7n9zkwYyME2PLLcUt%2FSLO0akEsCx9RpiUq3nYdmOznxs%2FlBQkT5aTY4KEshbvTAAarbiIu8U8dohI9kczoopw4m8nfIb2sSosa5YTCflNDksz7C27naxH%2BU8BD3MM3Vule7L4%2Feb3mbcBvudCVsus%2B3Ia1L8aIdUcW5I5fMgdMlMVYVTH7AUMPVOU44AqCgVj%2BqFehAgqRfCQQKXVodWFDHKlsE0cjkKb7UBQoXlzVra6978x%2Fpwa98Fb3VQxaRCt9bOF0ePq4KMmPcNjVkoq4NJILIdAezle7%2BR3nqmEO8VHnyH0aoLiGpiYpPxGlI9iyn2rJuqx2%2B%2BxYAEBD2Pi3B10Pprou6qSlMOvg178GOuQC0SeYjJn33SCQh79dfMj%2BelRblGFpSJcnSSswmmsm2s5ME28ky3V6tSd2J7uOudBkseltPYUyTPGC4v4XelQVXXaX9SQcA6HGDk1lUtdoiiPIMB8nKybJb8LJolJQmNT3adkDu7pKQLYZqEAMTf3qsWweYCpceZdT2Bw3MN2HbRsuSEpGq9uke4lk%2BuBbl0RTyrK4zwyxDP0XjpoKgXxODHXWBauGlxORHhCN%2B3BNfk9%2FK886bQtkAauBtEtlqZXIwJFnUIPZCPlDe65KNNbVKgYI5MfHPJ7eDqycCGfuuY%2B2iqMlB3fZxwTg%2Bj8%2BsplcjAzcL7EjtnxEYFk0rkB%2FDMqhaSgNxjGzKjQEuYl481kUTKrUY0%2BeNCjq6Dy%2FsEbEuU%2Ff496V2riJqR5WxZgcZaF3s8e%2FrkD32m97OqiFgNWFrU8jw5MxZlouPmd6BYu%2FrAJLpbTe4iuKbbuLhP4NIkVzB%2Bs%3D&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIASUVKZWSTKMYEAGLO%2F20250409%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250409T095454Z&X-Amz-Expires=43200&X-Amz-SignedHeaders=host&X-Amz-Signature=d24e04cfad1b6894b540675d5f7a2d6d7afb45bbd0935bca8edb732cd064ce31"
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
