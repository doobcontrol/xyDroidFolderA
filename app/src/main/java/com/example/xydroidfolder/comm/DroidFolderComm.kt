package com.example.xydroidfolder.comm

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import java.io.File

class DroidFolderComm(
    localIp: String, localPort: Int,
    targetIp: String, targetPort: Int,
    val workScope: CoroutineScope
) {
    val tAG: String = "DroidFolderComm"

    val myXyUdpComm: IXyComm

    init {
        myXyUdpComm = XyUdpComm(
            localIp, localPort,
            targetIp, targetPort,
            workScope,
            ::XyCommRequestHandler)
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

    fun XyCommRequestHandler(receivedString: String): String{
        val commData = CommData.fromCommPkgString(receivedString)
        val commResult = CommResult(commData.cmdID)

        when(commData.cmd){
            DroidFolderCmd.GetInitFolder -> {
                val directory =
                    Environment
                        .getExternalStorageDirectory()
                        .path.toString()
                getFolderContent(commResult, directory)
            }
            DroidFolderCmd.GetFolder -> {
                val directory =
                    Environment
                        .getExternalStorageDirectory()
                        .path.toString()

                getFolderContent(commResult,
                    directory + "/" +
                    (commData.cmdParDic[CmdPar.requestPath]?.replace("\\", "/") ?: "")
                )
            }
            else -> {}
        }

        return commResult.toCommPkgString()
    }
    private fun getFolderContent(commResult: CommResult, folderName: String){
        Log.d(tAG, "folderName: $folderName")
        val directory = File(folderName)

        val directoryList = directory.listFiles()
        Log.d(tAG, "directoryList: " + directoryList?.count())

        val files = directoryList?.filter { it.isFile }
        Log.d(tAG, "files: " + files?.count())
        var filesStr = StringBuilder()
        files?.forEach { file ->
            if(filesStr.isNotEmpty()){
                filesStr.append("|")
            }
            filesStr.append(file.name)
        }
        commResult.resultDataDic[CmdPar.files] = filesStr.toString();

        val folders = directoryList?.filter { it.isDirectory }
        Log.d(tAG, "folders: " + folders?.count())
        var foldersStr = StringBuilder()
        folders?.forEach { folder ->
            if(foldersStr.isNotEmpty()){
                foldersStr.append("|")
            }
            foldersStr.append(folder.name)
        }
        commResult.resultDataDic[CmdPar.folders] = foldersStr.toString();
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