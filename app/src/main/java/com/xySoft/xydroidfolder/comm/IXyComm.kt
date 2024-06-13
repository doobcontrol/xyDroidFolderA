package com.xySoft.xydroidfolder.comm

import java.io.InputStream

interface IXyComm {
    fun startListen()
    suspend fun sendForResponse(sendData: String):String
    fun clean()
    suspend fun prepareStreamReceiver(
        file: String,
        fileLength: Long,
        streamReceiverPar: String
        )
    suspend fun sendStream(
        fileStream: InputStream,
        fileLength: Long,
        streamReceiverPar: String
    )
}