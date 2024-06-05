package com.xySoft.xydroidfolder.comm

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
        file: String,
        fileLength: Long,
        streamReceiverPar: String
    )
}