package com.example.xydroidfolder.comm

import java.util.UUID

class CommData(
    val cmd: DroidFolderCmd,
    val cmdParDic: MutableMap<CmdPar, String> = mutableMapOf()
) {
    companion object {
        fun fromCommPkgString(pkgString: String): CommData {
            var cmd: DroidFolderCmd = DroidFolderCmd.NONE
            val cmdParDic: MutableMap<CmdPar, String> = mutableMapOf()

            val paramList = pkgString.split(",")
            paramList.forEach {
                val param = it.split("=")
                val key = param[0]
                val value = param[1]
                if(key == CmdPar.cmd.name) cmd = DroidFolderCmd.valueOf(value)
                else{
                    cmdParDic[CmdPar.valueOf(key)] = value
                }
            }

            return CommData(
                cmd=cmd,
                cmdParDic=cmdParDic)
        }
    }

    val cmdID: String = UUID.randomUUID().toString()

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
            pkgString.append("${k.name}=$v")
        }
        return pkgString.toString()
    }
    fun toCommPkgBytes(): ByteArray {
        return toCommPkgString().toByteArray()
    }
}