package com.xySoft.xydroidfolder.comm

import java.util.UUID

class CommData(
    val cmd: DroidFolderCmd,
    val cmdID: String = UUID.randomUUID().toString(),
    val cmdParDic: MutableMap<CmdPar, String> = mutableMapOf()
) {
    companion object {
        fun fromCommPkgString(pkgString: String): CommData {
            var cmd: DroidFolderCmd = DroidFolderCmd.NONE
            var cmdID: String? = null
            val cmdParDic: MutableMap<CmdPar, String> = mutableMapOf()

            val paramList = pkgString.split(",")
            paramList.forEach {
                val param = it.split("=")
                val key = param[0]
                val value = param[1]
                if(key == CmdPar.cmd.name) cmd = DroidFolderCmd.valueOf(value)
                else if(key == CmdPar.cmdID.name) cmdID = value
                else{
                    cmdParDic[CmdPar.valueOf(key)] = CommStringEncode.decodeParString(value)
                }
            }

            return CommData(
                cmd=cmd,
                cmdID = cmdID!!,
                cmdParDic=cmdParDic)
        }
    }

    private fun toCommDic(): MutableMap<CmdPar, String> {
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
            pkgString.append("${k.name}=${CommStringEncode.encodeParString(v)}")
        }
        return pkgString.toString()
    }
}