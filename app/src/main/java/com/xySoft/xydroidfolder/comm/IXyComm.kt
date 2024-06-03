package com.xySoft.xydroidfolder.comm

interface IXyComm {
    fun startListen()
    suspend fun sendForResponse(sendData: String):String
    fun clean()
}