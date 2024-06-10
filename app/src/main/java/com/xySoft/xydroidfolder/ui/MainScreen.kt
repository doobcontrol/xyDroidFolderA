package com.xySoft.xydroidfolder.ui

import android.net.InetAddresses
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xySoft.xydroidfolder.FileTransInfo
import com.xySoft.xydroidfolder.R
import com.xySoft.xydroidfolder.ui.theme.XyDroidFolderATheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig
import java.text.DecimalFormat

@Composable
fun MainScreen(
    sharedText: String?,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    pasteToPc: () -> Unit = {},
    startFileService: (String) -> Unit = {}
) {
    val mainScreenState: MainScreenState by viewModel.mainScreenState.collectAsStateWithLifecycle(
        //Dealing with exception: CompositionLocal LocalLifecycleOwner not present
        //3. Manually Pass `LocalLifecycleOwner`
        lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    )

    val stopService: () -> Unit = { viewModel.stopService() }

    var scanErrorInfo by rememberSaveable { mutableStateOf("") }
    val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanCustomCode())
    { result ->
        // handle QRResult
        when(result){
            is QRResult.QRSuccess -> {
                val scanQrCodeInfo = result.content.rawValue
                var isValidIpaddress = false
                if(!scanQrCodeInfo.isNullOrEmpty()) {
                    val ipInfoList = scanQrCodeInfo.split(":")
                    if (ipInfoList.size >= 2) {
                        if (Build.VERSION.SDK_INT >= 29) {
                            if (InetAddresses.isNumericAddress(ipInfoList[0])) {
                                isValidIpaddress = true
                            }
                        }
                        else {
                            if (Patterns.IP_ADDRESS.matcher(ipInfoList[0]).matches()) {
                                isValidIpaddress = true
                            }
                        }
                        if (isValidIpaddress) {
                            var portValid = false
                            if(ipInfoList[1].toIntOrNull() != null
                                && ipInfoList[1].toInt() in 0..65535){
                                portValid = true
                            }
                            if(!portValid){
                                isValidIpaddress = false
                            }
                        }
                    }
                }

                if(isValidIpaddress){
                    scanErrorInfo = ""
                    scanQrCodeInfo?.let { startFileService(it) }
                }
                else{
                    scanErrorInfo = "Invalid IP address: $scanQrCodeInfo"
                }
            }
            is QRResult.QRUserCanceled -> {
                // User canceled
            }
            is QRResult.QRMissingPermission  -> {
                scanErrorInfo = "QRMissingPermission"
            }
            is QRResult.QRError -> {
                scanErrorInfo = "QRError"
            }
        }
    }

    val barCodeButtonClick: () -> Unit = { scanQrCodeLauncher.launch(
        ScannerConfig.build {
            setOverlayStringRes(R.string.scan_barcode) // string resource used for the scanner overlay
        })
    }

    var inputSharedText by rememberSaveable { mutableStateOf(sharedText) }
    if(!inputSharedText.isNullOrEmpty()
        && mainScreenState.isRunning){
        Log.d("MainScreen", "Send shared text: $sharedText")
        viewModel.sendText(inputSharedText.toString())
        inputSharedText = null
    }

    MainScreenStateless(
        scanErrorInfo = scanErrorInfo,
        mainScreenState = mainScreenState,
        stopService = stopService,
        barCodeButtonClick = barCodeButtonClick,
        pasteButtonClick = pasteToPc,
        modifier = modifier
    )
}

@Composable
fun MainScreenStateless(
    mainScreenState: MainScreenState,
    stopService: () -> Unit,
    modifier: Modifier = Modifier,
    pasteButtonClick: () -> Unit = {},
    barCodeButtonClick: () -> Unit = {},
    scanErrorInfo: String = ""
){
    val isServiceRunning = mainScreenState.isRunning
    val messages = mainScreenState.messages

    Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
        .fillMaxSize()
        .padding(8.dp)
    ) {
        if(!isServiceRunning){
            Image(
                painter = painterResource(R.drawable.xyfolderimage),
                contentDescription = null,
                modifier = Modifier.padding(16.dp))
            Button(onClick = barCodeButtonClick) {
                Text(stringResource(R.string.scan_barcode))
            }
            if(scanErrorInfo.isNotEmpty()){
                Text(scanErrorInfo)
            }
        }
        else{
            Spacer(modifier = Modifier
                .padding(8.dp)
                .weight(1f))

            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier.height(112.dp)
            ) {
                Text(stringResource(R.string.targetPC) + mainScreenState.targetPC)

                Row {
                    Button(
                        onClick = stopService
                    ) {
                        Text(stringResource(R.string.stopService))
                    }
                    Button(
                        onClick = pasteButtonClick
                    ) {
                        Text(stringResource(R.string.paste))
                    }
                }

                val inFileTransfer = mainScreenState.inFileTransfer
                if(inFileTransfer){
                    val progress = mainScreenState.fileProgress.toFloat() /
                            mainScreenState.fileTransInfo!!.totalLong
                    val percentage =  DecimalFormat("00.00").format(progress * 100)
                    Text("$percentage(${mainScreenState.fileProgress} " +
                            "/ ${mainScreenState.fileTransInfo.totalLong})")
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier
                .padding(8.dp)
                .weight(1f))
            LazyColumn(modifier = Modifier
                .padding(8.dp)
                .weight(1f)) {
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
fun GreetingPreviewNotRunning() {
    val mainScreenState = MainScreenState(
        isRunning = false,
        messages = mutableListOf(),
        inFileTransfer = false,
        isFileIn = false,
        fileProgress = 0,
        fileTransInfo = null)

    XyDroidFolderATheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            MainScreenStateless(
                mainScreenState = mainScreenState,
                stopService = { },
                barCodeButtonClick = { },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreviewRunning() {
    val messages = mutableListOf(
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",
        "request E:\\hb\\VmUse\\PerVm\\ubuntu\\temp"
    )
    val fileTransInfo = FileTransInfo(
        true,"E:\\hb\\VmUse\\PerVm\\ubuntu\\temp",10000
    )
    val mainScreenState = MainScreenState(
        isRunning = true,
        targetPC= "192.168.3.100:8080",
        messages = messages,
        inFileTransfer = true,
        isFileIn = false,
        fileProgress = 4800,
        fileTransInfo = fileTransInfo)

    XyDroidFolderATheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            MainScreenStateless(
                mainScreenState = mainScreenState,
                stopService = { },
                barCodeButtonClick = { },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}