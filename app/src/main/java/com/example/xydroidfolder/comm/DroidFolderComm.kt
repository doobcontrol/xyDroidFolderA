package com.example.xydroidfolder.comm

import kotlinx.coroutines.CoroutineScope

class DroidFolderComm(
    localIp: String, localPort: Int,
    targetIp: String, targetPort: Int,
    val workScope: CoroutineScope
) {
    val myXyUdpComm: IXyComm

    init {
        myXyUdpComm = XyUdpComm(
            localIp, localPort,
            targetIp, targetPort,
            workScope)
        myXyUdpComm.startListen()
    }

    private fun Request(commData: CommData): CommResult{
        return CommResult(
            myXyUdpComm.sendForResponse(commData.toCommPkgString())
        )
    }

    fun Register(
        ip: String,
        port: Int,
        hostName: String): CommResult
    {
        val cmdParDic = mutableMapOf<CmdPar, String>()
        cmdParDic[CmdPar.ip] = ip
        cmdParDic[CmdPar.port] = port.toString()
        cmdParDic[CmdPar.hostName] = hostName

        val commData = CommData(DroidFolderCmd.Register, cmdParDic);

        return Request(commData);
    }
}

enum class DroidFolderCmd {
    NONE,
    Register,
    GetInitFolder,
    GetFolder,
    GetFile,
    SendFile
}
enum class CmdPar {
    cmd,
    cmdID,
    cmdSucceed,
    ip,
    port,
    hostName,
    requestPath,
    targetFile,
    fileLength,
    streamReceiverPar,
    folders,
    files,
    returnMsg,
    errorMsg
}