package com.example.xydroidfolder.comm

interface IXyComm {
    fun startListen()
    suspend fun sendForResponse(sendData: String):String

}