package com.xySoft.xydroidfolder.comm

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream


class DroidFolderComm(
    localIp: String, localPort: Int,
    targetIp: String, targetPort: Int,
    private val workScope: CoroutineScope,
    val xyCommRequestHandler: (CommData, CommResult) -> Unit,
    val fileTransEventHandler: (FileTransEventType, FileInOut?, String?, Long, Long) -> Unit
) {
    private val tAG: String = "DroidFolderComm"

    private val myXyUdpComm: IXyComm

    init {
        myXyUdpComm = XyUdpComm(
            localIp, localPort,
            targetIp, targetPort,
            workScope,
            ::xyCommRequestHandler,
            ::fileProgressNoteHandler)
        myXyUdpComm.startListen()
    }

    fun clean(){
        myXyUdpComm.clean()
    }

    private suspend fun request(commData: CommData): CommResult{
        val commResult: CommResult = CommResult.fromCommPkgString(
            myXyUdpComm.sendForResponse(commData.toCommPkgString())
        )
        if(commResult.cmdID != commData.cmdID){
            commResult.errorCmdID = true
        }
        return commResult
    }

    suspend fun register(
        ip: String,
        port: Int,
        hostName: String): CommResult
    {
        val cmdParDic = mutableMapOf<CmdPar, String>()
        cmdParDic[CmdPar.ip] = ip
        cmdParDic[CmdPar.port] = port.toString()
        cmdParDic[CmdPar.hostName] = hostName

        val commData = CommData(DroidFolderCmd.Register, cmdParDic = cmdParDic)

        return request(commData)
    }
    suspend fun sendText(text: String): CommResult
    {
        val cmdParDic = mutableMapOf<CmdPar, String>()
        cmdParDic[CmdPar.text] = text

        val commData = CommData(DroidFolderCmd.SendText, cmdParDic = cmdParDic)
        Log.d(tAG, text)
        return request(commData)
    }

    val sendStreamJobMap = mutableMapOf<String, Job>()
    suspend fun sendFile(
        inputStream: InputStream,
        fileName: String,
        fileLength: Long
    ): CommResult
    {

        val cmdParDic = mutableMapOf<CmdPar, String>()
        cmdParDic[CmdPar.targetFile] = fileName
        cmdParDic[CmdPar.fileLength] = fileLength.toString()

        val commData = CommData(DroidFolderCmd.SendFile, cmdParDic = cmdParDic)
        val commResult = request(commData)

        //start send file
        //workScope.launch {
        fileTransEventHandler(
            FileTransEventType.Start,
            FileInOut.Out,
            fileName,
            fileLength,
            0
        )
        val job = workScope.launch {
            myXyUdpComm.sendStream(
                inputStream,
                fileLength,
                commResult.resultDataDic[CmdPar.streamReceiverPar]!!
            )
        }
        sendStreamJobMap[commData.cmdID] = job
        job.join()
        if(sendStreamJobMap.containsKey(commData.cmdID)){
            sendStreamJobMap.remove(commData.cmdID)
        }

        fileTransEventHandler(
            FileTransEventType.End,
            FileInOut.Out,
            fileName,
            0,
            0
        )
        //}

        return commResult
    }

    private fun xyCommRequestHandler(receivedString: String): String{
        val commData = CommData.fromCommPkgString(receivedString)
        val commResult = CommResult(commData.cmdID)

        if(commData.cmdParDic.containsKey(CmdPar.text)){
            commData.cmdParDic[CmdPar.text] =
                commData.cmdParDic[CmdPar.text]!!
        }
        xyCommRequestHandler(commData, commResult)

        val directory =
            Environment
                .getExternalStorageDirectory()
                .path.toString()

        when(commData.cmd){
            DroidFolderCmd.GetInitFolder -> {
                getFolderContent(commResult, directory)
            }
            DroidFolderCmd.GetFolder -> {
                getFolderContent(commResult,
                    directory + "/" +
                    (commData.cmdParDic[CmdPar.requestPath]?.replace("\\", "/") ?: "")
                )
            }
            DroidFolderCmd.SendFile -> {
                val fileName = "$directory/" +
                        (commData.cmdParDic[CmdPar.targetFile]?.replace("\\", "/")?:"")

                workScope.launch {
                    fileTransEventHandler(
                        FileTransEventType.Start,
                        FileInOut.In,
                        fileName,
                        commData.cmdParDic[CmdPar.fileLength]!!.toLong(),
                        0
                        )

                    myXyUdpComm.prepareStreamReceiver(
                        fileName,
                        commData.cmdParDic[CmdPar.fileLength]!!.toLong(),
                        commResult.resultDataDic[CmdPar.streamReceiverPar]!!)
                    confirmReceiveFileSucceed(commData.cmdID)

                    fileTransEventHandler(
                        FileTransEventType.End,
                        FileInOut.In,
                        fileName,
                        0,
                        0
                    )
                }
            }
            DroidFolderCmd.GetFile -> {
                val fileName = "$directory/" +
                        (commData.cmdParDic[CmdPar.targetFile]?.replace("\\", "/")?:"")
                val fileLength = File(fileName).length()
                commResult.resultDataDic[CmdPar.fileLength] = fileLength.toString()

                workScope.launch {
                    fileTransEventHandler(
                        FileTransEventType.Start,
                        FileInOut.Out,
                        fileName,
                        fileLength,
                        0
                    )

                    val fileObj = File(fileName)
                    val job = workScope.launch {
                        myXyUdpComm.sendStream(
                            fileObj.inputStream(),
                            fileLength,
                            commData.cmdParDic[CmdPar.streamReceiverPar]!!
                        )
                    }
                    sendStreamJobMap[commData.cmdID] = job
                    job.join()
                    if(sendStreamJobMap.containsKey(commData.cmdID)){
                        sendStreamJobMap.remove(commData.cmdID)
                    }

                    fileTransEventHandler(
                        FileTransEventType.End,
                        FileInOut.Out,
                        fileName,
                        0,
                        0
                    )
                }
            }
            DroidFolderCmd.SendText -> {
                //
            }
            DroidFolderCmd.SendFileSucceed -> {
                val sendFileCmdID: String? =
                    commData.cmdParDic[CmdPar.sendFileCmdID]
                if(sendStreamJobMap.containsKey(sendFileCmdID)){
                    sendStreamJobMap[sendFileCmdID]!!.cancel()
                    sendStreamJobMap.remove(sendFileCmdID)
                }
            }
            else -> {}
        }

        return commResult.toCommPkgString()
    }

    private fun confirmReceiveFileSucceed(cmdID: String) {
        val commData = CommData(DroidFolderCmd.SendFileSucceed)
        commData.cmdParDic[CmdPar.sendFileCmdID] = cmdID

        workScope.launch {
            try {
                request(commData)
            } catch (e: Exception) {
                Log.d(tAG, "confirmReceiveFileSucceed error: $e")
            }
        }
    }

    private fun getFolderContent(commResult: CommResult, folderName: String){
        Log.d(tAG, "folderName: $folderName")
        val directory = File(folderName)

        val directoryList = directory.listFiles()
        Log.d(tAG, "directoryList: " + directoryList?.count())

        val files = directoryList?.filter { it.isFile }
        Log.d(tAG, "files: " + files?.count())
        val filesStr = StringBuilder()
        files?.forEach { file ->
            if(filesStr.isNotEmpty()){
                filesStr.append("|")
            }
            filesStr.append(file.name)
        }
        commResult.resultDataDic[CmdPar.files] = filesStr.toString()

        val folders = directoryList?.filter { it.isDirectory }
        Log.d(tAG, "folders: " + folders?.count())
        val foldersStr = StringBuilder()
        folders?.forEach { folder ->
            if(foldersStr.isNotEmpty()){
                foldersStr.append("|")
            }
            foldersStr.append(folder.name)
        }
        commResult.resultDataDic[CmdPar.folders] = foldersStr.toString()
    }

    private fun fileProgressNoteHandler(progress: Long){
        fileTransEventHandler(
            FileTransEventType.Progress,
            null,
            null,
            0,
            progress
        )
    }
}

enum class DroidFolderCmd {
    NONE,
    Register,
    GetInitFolder,
    GetFolder,
    GetFile,
    SendFile,
    SendFileSucceed,
    SendText
}
enum class CmdPar {
    cmd,
    cmdID,
    sendFileCmdID,
    cmdSucceed,
    ip,
    port,
    hostName,
    requestPath,
    targetFile,
    text,
    fileLength,
    streamReceiverPar,
    folders,
    files,
    returnMsg,
    errorMsg
}
enum class FileInOut {
    In,
    Out
}
enum class FileTransEventType {
    Start,
    End,
    Progress
}