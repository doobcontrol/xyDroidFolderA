package com.example.xydroidfolder.comm

class CommResult(
    var cmdID: String,
    var cmdSucceed: Boolean = false,
    var errorCmdID: Boolean = false,
    val resultDataDic: MutableMap<CmdPar, String> = mutableMapOf()
) {
    companion object {
        fun fromCommPkgString(pkgString: String): CommResult {
            var cmdID = ""
            var cmdSucceed = false
            var errorCmdID = true
            val resultDataDic: MutableMap<CmdPar, String> = mutableMapOf()

            val paramList = pkgString.split(",")
            paramList.forEach {
                val param = it.split("=")
                val key = param[0]
                val value = param[1]
                if(key == CmdPar.cmdID.name) {
                    cmdID = value
                    errorCmdID = false
                }
                else if(key == CmdPar.cmdSucceed.name) cmdSucceed = value.toBoolean()
                else{
                    resultDataDic[CmdPar.valueOf(key)] = value
                }
            }

            return CommResult(
                cmdID=cmdID,
                cmdSucceed=cmdSucceed,
                errorCmdID=errorCmdID,
                resultDataDic=resultDataDic)
        }
    }

    private fun toCommDic(): MutableMap<CmdPar, String> {
        val commDic = resultDataDic.toMutableMap()
        commDic[CmdPar.cmdID] = cmdID
        commDic[CmdPar.cmdSucceed] = cmdSucceed.toString()
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