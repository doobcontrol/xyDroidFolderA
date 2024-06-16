package com.xySoft.xydroidfolder.comm

class CommStringEncode {
    companion object {
        private val encodeDic: MutableMap<String, String> = mutableMapOf(
            Pair(",", "xyCommA"),
            Pair("=", "xyEquaL"),
        )
        private val decodeDic : MutableMap<String, String> = mutableMapOf(
            Pair("xyCommA", ","),
            Pair("xyEquaL", "="),
        )

        fun encodeParString(parString: String): String
        {
            return stringReplace(parString, encodeDic)
        }
        fun decodeParString(parString: String): String
        {
            return stringReplace(parString, decodeDic)
        }
        private fun stringReplace(rString: String,
                                  rDic: MutableMap<String, String>): String
        {
            var retString = rString

            rDic.forEach {
                retString = retString.replace(it.key, it.value)
            }
            return retString
        }
    }
}