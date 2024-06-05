package com.xySoft.xydroidfolder.comm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress

class XyUdpComm(
    private val localIp: String, localPort: Int,
    private val targetIp: String, targetPort: Int,
    private val workScope: CoroutineScope,
    val xyCommRequestHandler: (String) -> String,
    val fileProgressNote: (Long) -> Unit
): IXyComm {
    private val tAG: String = "IXyComm"

    private var pkgLength: Int = 1024 * 32

    private var localPoint: SocketAddress? = null
    private var targetPoint: SocketAddress? = null
    init {
        localPoint = InetSocketAddress(localIp,localPort)
        targetPoint = InetSocketAddress(targetIp,targetPort)
    }
    private var inListening: Boolean = false
    private var listenerSocket: DatagramSocket? = null
    override fun startListen() {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        listenerSocket = DatagramSocket(localPoint)
        inListening = true
        workScope.launch {
            while(inListening){
                listenerSocket!!.receive(receivePacket)

                if(!inListening){
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
        inListening = false
        listenerSocket?.close()
        listenerSocket=null

        //clean wait socket for response if any
        socketList.forEach {
            it.close()
        }
        socketList.clear()
    }

    override suspend fun prepareStreamReceiver(
        file: String,
        fileLength: Long,
        streamReceiverPar: String
    ) {
        val receivePort = streamReceiverPar.toInt()
        val receiveSocket = withContext(Dispatchers.IO) {
            DatagramSocket(
                InetSocketAddress(localIp, receivePort)
            )
        }
        socketList.add(receiveSocket)

        Log.d(tAG, "localIp: $localIp port: $receivePort file: $file fileLength: $fileLength")

        var totalReceivedLength: Long = 0
        val totalSendFileLength: Long = fileLength

        withContext(Dispatchers.IO) {
            //write file task
            val receivedByteMap: MutableMap<Long, ByteArray> = mutableMapOf()
            var waitedPkgID: Long = 0
            var taskIsDone = false
            //write file task
            workScope.launch {
                val receiveFileStream = File(file).outputStream()

                while (!taskIsDone)
                {
                    try
                    {
                        if (receivedByteMap.isNotEmpty()
                            && totalSendFileLength > totalReceivedLength)
                        {
                            if (receivedByteMap.containsKey(waitedPkgID))
                            {
                                val tByte = receivedByteMap[waitedPkgID]

                                receiveFileStream.write(
                                    tByte,
                                    0,
                                    tByte!!.count())
                                totalReceivedLength += tByte.count()
                                fileProgressNote(totalReceivedLength)

                                synchronized (receivedByteMap)
                                {
                                    receivedByteMap.remove(waitedPkgID)
                                }
                                waitedPkgID++
                            }
                        }

                        if (totalSendFileLength == totalReceivedLength)
                        {
                            Log.d(tAG, "writeTask done")
                            receiveSocket.close()
                            socketList.remove(receiveSocket)
                            taskIsDone = true
                            break
                        }
                    }
                    catch (te: ThreadDeath)
                    {
                        Log.d(tAG, "ThreadDeath te: "
                                + te.message + "" + te.stackTrace)
                    }
                    catch (e: Exception)
                    {
                        Log.d(tAG, "Exception e: "
                                + e.message + "" + e.stackTrace)
                    }
                }

                receiveFileStream.close()
            }

            val receivedBytes = ByteArray(pkgLength + 8 + 8)
            val receivePacket = DatagramPacket(receivedBytes, receivedBytes.size)
            //read socket task
            workScope.launch {
                while (!taskIsDone)
                {
                    try
                    {
                        receiveSocket.receive(receivePacket)

                        //return pkg id
                        val receivedLength = receivePacket.length
                        val returnBytes = receivedBytes.copyOfRange(
                            receivedLength - 16,
                            receivedLength)
                        val sendPacket = DatagramPacket(
                            returnBytes,
                            returnBytes.size,
                            receivePacket.socketAddress)
                        receiveSocket.send(sendPacket)

                        val lengthByteArr = receivedBytes.copyOfRange(
                            receivedLength - 8,
                            receivedLength)

                        val lengthByte: Long = byteArrayToLong(lengthByteArr)

                        if (lengthByte == receivedLength.toLong() - 16)
                        {
                            val pkgID = receivedBytes.copyOfRange(
                                receivedLength - 16,
                                receivedLength - 8)

                            val pkgIDNumber: Long = byteArrayToLong(pkgID)

                            synchronized  (receivedByteMap)
                            {
                                if (pkgIDNumber >= waitedPkgID
                                    && !receivedByteMap.containsKey(pkgIDNumber))
                                {
                                    receivedByteMap[pkgIDNumber] = receivedBytes
                                        .copyOfRange(0, lengthByte.toInt())
                                }
                            }
                        }

                        if (totalSendFileLength == totalReceivedLength)
                        {
                            Log.d(tAG, "receiveTask done")
                            break
                        }
                    }
                    catch (e: Exception)
                    {
                        Log.d(tAG, "Exception e: "
                                + e.message + "" + e.stackTrace)
                    }

                }
            }
        }.join()
    }

    override suspend fun sendStream(
        file: String,
        fileLength: Long,
        streamReceiverPar: String)
    {
        val sendPort = streamReceiverPar.toInt()
        val maxBufferTaskCount = 10
        val maxSendTaskCount = 5

        val sendTaskDataList = mutableListOf<FileSendTask>()
        val inSendTaskDataDic = mutableMapOf<Long, FileSendTask>()

        val sendTasksDic = mutableMapOf<Long, Job>()

        withContext(Dispatchers.IO) {
            val sendLength = 1024 * 32
            var fileChunk: ByteArray
            var numBytes: Int

            var receivedSentBytes: Long = 0
            val fileStream = File(file).inputStream()

            var isReadFinish = false
            var sendNumber: Long = 0

            while (true) {
                if (!isReadFinish) {
                    if (sendTaskDataList.count() <= maxBufferTaskCount)
                    {
                        fileChunk = ByteArray(sendLength)
                        numBytes =
                            fileStream.read(fileChunk, 0, sendLength)
                        if (numBytes > 0) {
                            sendTaskDataList.add(
                                FileSendTask(
                                    sendNumber,
                                    fileChunk,
                                    numBytes
                                )
                            )
                            sendNumber++
                        } else {
                            fileStream.close()
                            isReadFinish = true
                        }
                    }
                }

                if (sendTaskDataList.isNotEmpty()
                    && sendTasksDic.count() <= maxSendTaskCount)
                {
                    val fst = sendTaskDataList.first()

                    synchronized (inSendTaskDataDic)
                    {
                        inSendTaskDataDic.put(fst.sendNumber, fst)
                    }

                    sendTaskDataList.remove(fst)

                    val tempJob = workScope.launch {
                        var sendSucceed = false
                        while (!sendSucceed)
                        {
                            try
                            {
                                sendSucceed = sendNumberedStream(
                                    fst.sendBytes,
                                    fst.sendLength,
                                    fst.sendNumber,
                                    sendPort
                                )
                            }
                            catch (e: Exception)
                            {
                                Log.d(tAG, "e: Exception: ${e.message}")
                            }
                        }
                        receivedSentBytes += fst.sendLength

                        fileProgressNote(receivedSentBytes)
                        synchronized (inSendTaskDataDic)
                        {
                            inSendTaskDataDic.remove(fst.sendNumber)
                        }

                        synchronized (sendTasksDic)
                        {
                            sendTasksDic.remove(fst.sendNumber)
                        }
                    }
                    synchronized (sendTasksDic)
                    {
                        sendTasksDic.put(fst.sendNumber, tempJob)
                    }
                }

                if (
                    isReadFinish &&
                    sendTaskDataList.isEmpty() &&
                    sendTasksDic.isEmpty()
                )
                {
                    break
                }
            }
        }
    }

    private suspend fun sendNumberedStream(
        sendBytes: ByteArray,
        sendLength: Int,
        sendNumber: Long,
        sendPort: Int
    ): Boolean{
        val idByte = byteArrayFromLong(sendNumber)
        val lengthByte = byteArrayFromLong(sendLength.toLong())

        //the last pkg length may not the array length
        val sendBuffer = sendBytes.copyOfRange(0, sendLength) + idByte + lengthByte

        val socket = withContext(Dispatchers.IO) {
            DatagramSocket()
        }
        socketList.add(socket)

        val sendPacket = DatagramPacket(
            sendBuffer,
            sendBuffer.size,
            InetSocketAddress(targetIp,sendPort)
        )

        withContext(Dispatchers.IO) {
            socket.send(sendPacket)
        }

        val receiveBuffer = ByteArray(16)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
        withContext(Dispatchers.IO) {
            socket.receive(receivePacket)
        }

        socket.close()
        socketList.remove(socket)

        if(receivePacket.length != 16){
            return false
        }
        else{
            val returnNumber = byteArrayToLong(receiveBuffer.copyOfRange(0, 8))
            val receivedSendLength = byteArrayToLong(receiveBuffer.copyOfRange(8, 16)).toInt()

            return (returnNumber == sendNumber
                    && receivedSendLength == sendLength)
        }
    }

    private fun byteArrayToLong(byteArray: ByteArray) : Long{
        var value = 0L
        var index = 0

        while(index < Long.SIZE_BYTES){
            value = value.shl(8) + byteArray[7 - index].toLong().and(0xFF)
            index++
        }
        return value
    }
    private fun byteArrayFromLong(value : Long) : ByteArray{
        val byteArray = ByteArray(Long.SIZE_BYTES)
        var index = 0
        var shift = 8 * Long.SIZE_BYTES

        while(index < Long.SIZE_BYTES){
            shift -= 8
            byteArray[7 - index] = value.shr(shift).and(0xFFL).toByte()
            index++
        }

        return byteArray
    }
}

data class FileSendTask(
    val sendNumber: Long,
    val sendBytes: ByteArray,
    val sendLength: Int
)