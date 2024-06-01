package com.example.xydroidfolder.comm

interface IXyComm {
    fun startListen()
    fun sendForResponse(sendData: String):String

}