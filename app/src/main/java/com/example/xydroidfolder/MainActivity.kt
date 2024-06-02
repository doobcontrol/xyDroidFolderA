package com.example.xydroidfolder

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xydroidfolder.ui.theme.XyDroidFolderATheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig

class MainActivity : ComponentActivity() {
    private val tAG: String = "MainActivity"
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            Log.d(tAG, "${it.key} = ${it.value}")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

            requestMultiplePermissions.launch(
                arrayOf(
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO",
                    "android.permission.READ_EXTERNAL_STORAGE"
                )
            )

        enableEdgeToEdge()
        setContent {
            XyDroidFolderATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GetQRCodeExample(
                        startFileService = { startFileService(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startFileService(pcAddress: String){
        val intent = Intent(this, XyFileService::class.java)
        intent.putExtra("key", pcAddress)
        startService(Intent(intent))
    }
}

@Composable
fun GetQRCodeExample(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    startFileService: (String) -> Unit = {}
) {
    val isServiceRunning by viewModel.isRunningStateFlow.collectAsStateWithLifecycle(
        //Dealing with exception: CompositionLocal LocalLifecycleOwner not present
        //3. Manually Pass `LocalLifecycleOwner`
        lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    )

    var scanQrCodeInfo by remember { mutableStateOf<String?>("...") }
    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanCustomCode())
    { result ->
        // handle QRResult
        when(result){
            is QRResult.QRSuccess -> {
                scanQrCodeInfo=result.content.rawValue
                scanQrCodeInfo?.let { startFileService(it) }
            }
            is QRResult.QRUserCanceled -> {
                scanQrCodeInfo="QRUserCanceled"
            }
            is QRResult.QRMissingPermission  -> {
                scanQrCodeInfo="QRMissingPermission"
            }
            is QRResult.QRError -> {
                scanQrCodeInfo="QRError"
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        Button(onClick = { scanQrCodeLauncher.launch(
            ScannerConfig.build {
                setOverlayStringRes(R.string.scan_barcode) // string resource used for the scanner overlay
            })
        }) {
            Text(stringResource(R.string.scan_barcode))
        }
        Text(scanQrCodeInfo?:"...")
        Text(if(isServiceRunning) "Service is Running" else "Service not Running")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    XyDroidFolderATheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            GetQRCodeExample(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}