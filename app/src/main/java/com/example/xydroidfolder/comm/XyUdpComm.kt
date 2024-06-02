package com.example.xydroidfolder.comm

import android.util.Log
import com.example.xydroidfolder.XyFileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    override fun startListen() {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        val listenerSocket = DatagramSocket(localPoint)

        workScope.launch {
            while(XyFileService.isRunning){
                listenerSocket.receive(receivePacket)

                val receivedData = receiveBuffer.copyOfRange(0, receivePacket.length)
                Log.d(tAG, "received: "
                        + receivedData.toString(Charsets.UTF_8))

                val responseString = xyCommRequestHandler(receivedData.toString(Charsets.UTF_8))
                val sendBateArray = responseString.toByteArray(Charsets.UTF_8)
                val sendPacket = DatagramPacket(
                    sendBateArray,
                    sendBateArray.size,
                    receivePacket.socketAddress)
                listenerSocket.send(sendPacket)
            }
        }
    }

    override fun sendForResponse(sendData: String): String {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        workScope.launch {
            val socket = DatagramSocket()
            val sendBateArray = sendData.toByteArray(Charsets.UTF_8)
            val sendPacket = DatagramPacket(
                sendBateArray,
                sendBateArray.size,
                targetPoint)

            socket.send(sendPacket)
            Log.d(tAG, "sent packet")

            Log.d(tAG, "start receive ...")
            socket.receive(receivePacket)
            Log.d(tAG, "end receive")
            Log.d(tAG, "received: "
                    + receiveBuffer.copyOfRange(0, receivePacket.length).toString(Charsets.UTF_8))

            socket.close()
        }
        return receiveBuffer.copyOfRange(0, receivePacket.length).toString(Charsets.UTF_8)
    }
}