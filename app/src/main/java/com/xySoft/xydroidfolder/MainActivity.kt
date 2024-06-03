package com.xySoft.xydroidfolder

import android.Manifest.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xySoft.xydroidfolder.ui.theme.XyDroidFolderATheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig

class MainActivity : ComponentActivity() {
    private val tAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!checkStoragePermissions()){
            requestForStoragePermissions()
        }

        enableEdgeToEdge()
        setContent {
            XyDroidFolderATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        startFileService = { startFileService(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkStoragePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11 (R) or above
            return Environment.isExternalStorageManager()
        } else {
            //Below android 11
            val write =
                ContextCompat.checkSelfPermission(this,
                    permission.WRITE_EXTERNAL_STORAGE)
            val read =
                ContextCompat.checkSelfPermission(this,
                    permission.READ_EXTERNAL_STORAGE)

            return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    private val storagePermissionCode = 23
    private fun requestForStoragePermissions() {
        //Android is 11 (R) or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent()
                intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.setData(uri)
                storageActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storageActivityResultLauncher.launch(intent)
            }
        } else {
            //Below android 11
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    permission.WRITE_EXTERNAL_STORAGE,
                    permission.READ_EXTERNAL_STORAGE
                ),
                storagePermissionCode
            )
        }
    }
    private val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(tAG, "$result")
        }

    private fun startFileService(pcAddress: String){
        val intent = Intent(this, XyFileService::class.java)
        intent.putExtra(XyFileService.PC_ADDRESS, pcAddress)
        startService(Intent(intent))
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    startFileService: (String) -> Unit = {}
) {
    val mainScreenState: MainScreenState by viewModel.mainScreenState.collectAsStateWithLifecycle(
        //Dealing with exception: CompositionLocal LocalLifecycleOwner not present
        //3. Manually Pass `LocalLifecycleOwner`
        lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    )

    val isServiceRunning = mainScreenState.isRunning
    val messages = mainScreenState.messages

    var scanQrCodeInfo by remember { mutableStateOf<String?>(null) }
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
        if(!isServiceRunning){
            Button(onClick = { scanQrCodeLauncher.launch(
                ScannerConfig.build {
                    setOverlayStringRes(R.string.scan_barcode) // string resource used for the scanner overlay
                })
            }) {
                Text(stringResource(R.string.scan_barcode))
            }
        }
        else{
            Text("target: "  + (scanQrCodeInfo?: "No QR Code"))

            LazyColumn {
                messages.forEach {
                    item {
                        Text(it)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    XyDroidFolderATheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            MainScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}