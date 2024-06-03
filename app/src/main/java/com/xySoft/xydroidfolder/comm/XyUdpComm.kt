package com.xySoft.xydroidfolder.comm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress

class XyUdpComm(
    localIp: String, localPort: Int,
    targetIp: String, targetPort: Int,
    private val workScope: CoroutineScope,
    val xyCommRequestHandler: (String) -> String
): IXyComm {
    private val tAG: String = "IXyComm"

    private var localPoint: SocketAddress? = null
    private var targetPoint: SocketAddress? = null
    init {
        localPoint = InetSocketAddress(localIp,localPort)
        targetPoint = InetSocketAddress(targetIp,targetPort)
    }
    private var inListenning: Boolean = false
    private var listenerSocket: DatagramSocket? = null
    override fun startListen() {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        listenerSocket = DatagramSocket(localPoint)
        inListenning = true
        workScope.launch {
            while(inListenning){
                listenerSocket!!.receive(receivePacket)

                if(!inListenning){
                    break
                }

                val receivedData = receiveBuffer.copyOfRange(0, receivePacket.length)
                Log.d(tAG, "received: "
                        + receivedData.toString(Charsets.UTF_8))

                val responseString = xyCommRequestHandler(receivedData.toString(Charsets.UTF_8))
                val sendBateArray = responseString.toByteArray(Charsets.UTF_8)
                val sendPacket = DatagramPacket(
                    sendBateArray,
                    sendBateArray.size,
                    receivePacket.socketAddress)
                listenerSocket!!.send(sendPacket)
            }
        }
    }

    override suspend fun sendForResponse(sendData: String): String {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        val socket = withContext(Dispatchers.IO) {
            DatagramSocket()
        }
        socketList.add(socket)

        val sendBateArray = sendData.toByteArray(Charsets.UTF_8)
        val sendPacket = DatagramPacket(
            sendBateArray,
            sendBateArray.size,
            targetPoint
        )

        withContext(Dispatchers.IO) {
            socket.send(sendPacket)
        }
        Log.d(tAG, "sent packet")

        Log.d(tAG, "start receive ...")
        withContext(Dispatchers.IO) {
            socket.receive(receivePacket)
        }
        Log.d(tAG, "end receive")
        Log.d(
            tAG, "received: "
                    + receiveBuffer.copyOfRange(0, receivePacket.length)
                .toString(Charsets.UTF_8)
        )

        socket.close()
        socketList.remove(socket)

        return receiveBuffer.copyOfRange(0, receivePacket.length).toString(Charsets.UTF_8)
    }

    private val socketList = mutableListOf<DatagramSocket>()
    override fun clean() {
        //clean listener socket
        inListenning = false
        listenerSocket?.close()
        listenerSocket=null

        //clean wait socket for response if any
        socketList.forEach {
            it.close()
        }
        socketList.clear()
    }
}