package com.xySoft.xydroidfolder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xySoft.xydroidfolder.FileTransInfo
import com.xySoft.xydroidfolder.XyFileService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel : ViewModel() {
    val mainScreenState: StateFlow<MainScreenState> =
        XyFileService.ServiceState.map {
            MainScreenState(
                isRunning = it.isRunning,
                connectError = it.connectError,
                targetPC= it.targetPC,
                messages = it.messages,
                inFileTransfer = it.inFileTransfer,
                isFileIn = it.isFileIn,
                fileProgress = it.fileProgress,
                fileTransInfo = it.fileTransInfo)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainScreenState(messages = mutableListOf())
        )
    fun stopService() {
        XyFileService.stopService()
    }

    fun sendText(text: String) {
        XyFileService.sendText(text)
    }
}

data class MainScreenState(
    val isRunning: Boolean = false,
    val connectError: String? = null,
    val targetPC: String = "",
    val messages: MutableList<String>,
    val inFileTransfer: Boolean = false,
    val isFileIn: Boolean = false,
    val fileProgress: Long = 0,
    val fileTransInfo: FileTransInfo? = null
)