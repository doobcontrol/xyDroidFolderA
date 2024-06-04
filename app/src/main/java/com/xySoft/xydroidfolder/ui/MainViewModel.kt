package com.xySoft.xydroidfolder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                messages = it.messages)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainScreenState(messages = mutableListOf())
        )
}

data class MainScreenState(
    var isRunning: Boolean = false,
    var messages: MutableList<String>
)