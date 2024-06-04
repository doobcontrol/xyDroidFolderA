package com.xySoft.xydroidfolder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xySoft.xydroidfolder.R
import com.xySoft.xydroidfolder.ui.theme.XyDroidFolderATheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig

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

    var scanQrCodeInfo by rememberSaveable { mutableStateOf<String?>(null) }
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

            val inFileTransfer = mainScreenState.inFileTransfer
            if(inFileTransfer){
                val progress = mainScreenState.fileProgress.toFloat() /
                        mainScreenState.fileTransInfo!!.totalLong
                Text("Progress: $progress(${mainScreenState.fileProgress} / ${mainScreenState.fileTransInfo!!.totalLong})")
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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