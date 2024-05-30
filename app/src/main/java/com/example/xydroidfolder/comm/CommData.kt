package com.example.xydroidfolder.comm

import java.util.UUID

class CommData(
    val cmd: XyCommCmd,
    val cmdParDic: MutableMap<String, String> = mutableMapOf()
) {

    val cmdID: String = UUID.randomUUID().toString()

    fun toCommDic(): MutableMap<String, String> {
        val commDic = cmdParDic.toMutableMap()
        commDic[commDicKey_cmdID] = cmdID
        commDic[commDicKey_cmd] = cmd.name
        return commDic
    }
    fun toCommPkgString(): String {
        val pkgString = StringBuilder()
        toCommDic().forEach { (k, v) ->
            if (pkgString.isNotEmpty())
            {
                pkgString.append(",")
            }
            pkgString.append("$k=$v")
        }
        return pkgString.toString()
    }
    fun toCommPkgBytes(): ByteArray {
        return toCommPkgString().toByteArray()
    }


    companion object {
        const val commDicKey_cmdID: String = "cmdID"
        const val commDicKey_cmd: String = "cmd"
    }
}

enum class XyCommCmd {
    PassiveRegist,
//    ActiveGetInitFolder,
//    ActiveGetFolder,
//    ActiveGetFile,
//    ActiveSendFile
}