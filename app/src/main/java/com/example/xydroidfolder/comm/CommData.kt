package com.example.xydroidfolder.comm

import java.util.UUID

class CommData(
    val cmd: XyCommCmd,
    val cmdParDic: MutableMap<CmdPar, String> = mutableMapOf()
) {

    val cmdID: String = UUID.randomUUID().toString()

    fun toCommDic(): MutableMap<CmdPar, String> {
        val commDic = cmdParDic.toMutableMap()
        commDic[CmdPar.cmdID] = cmdID
        commDic[CmdPar.cmd] = cmd.name
        return commDic
    }
    fun toCommPkgString(): String {
        val pkgString = StringBuilder()
        toCommDic().forEach { (k, v) ->
            if (pkgString.isNotEmpty())
            {
                pkgString.append(",")
            }
            pkgString.append("${k.name}=$v")
        }
        return pkgString.toString()
    }
    fun toCommPkgBytes(): ByteArray {
        return toCommPkgString().toByteArray()
    }
}

enum class XyCommCmd {
    Register
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