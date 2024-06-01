package com.example.xydroidfolder

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.util.Log
import com.example.xydroidfolder.comm.CmdPar
import com.example.xydroidfolder.comm.CommData
import com.example.xydroidfolder.comm.XyCommCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class XyFileService : Service()  {
    val tAG: String = "XyFileService"

    private val job = SupervisorJob()
    private val receiveScope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        var isRunning: Boolean = false

        private val _uiState = MutableStateFlow(isRunning)
        // The UI collects from this StateFlow to get its state updates
        val ServiceRunningState: StateFlow<Boolean> = _uiState
    }

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
            try {
                val intent: Intent = msg.obj as Intent
                val pcAddress: String = intent.getStringExtra("key")!!

                Log.d(tAG, "sending packet:")
                val cmdParDic = mutableMapOf<CmdPar, String>()
                cmdParDic[CmdPar.ip] = "192.168.3.119"
                cmdParDic[CmdPar.port] = "12921"
                cmdParDic[CmdPar.hostName] = "Pixel 7"
                val commData = CommData(
                    XyCommCmd.Register,
                    cmdParDic)
                val sendBateArray: ByteArray = commData.toCommPkgBytes()
                Log.d(tAG, commData.toCommPkgString())

                Log.d(tAG, "Ip: " + InetAddress.getByName(pcAddress.split(":")[0]))
                Log.d(tAG, "Port: " +pcAddress.split(":")[1])

                val socket = DatagramSocket()
                val sendPacket = DatagramPacket(
                    sendBateArray,
                    sendBateArray.size,
                    InetAddress.getByName(pcAddress.split(":")[0]),
                    pcAddress.split(":")[1].toInt())

                socket.send(sendPacket)
                socket.close()
                Log.d(tAG, "sent packet")

                val receiveBuffer = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

//                val listenerSocket = DatagramSocket(
//                    12921,
//                    InetAddress.getByName("0.0.0.0"))
                val listenerSocket = DatagramSocket(
                    InetSocketAddress("192.168.3.119", 12921))
                Log.d(tAG, "start receive ...")
                listenerSocket.receive(receivePacket)
                Log.d(tAG, "end receive")
                Log.d(tAG, "received: "
                        + receiveBuffer.copyOfRange(0, receivePacket.length).toString(Charsets.UTF_8))

                receiveScope.launch {
                    while(isRunning){
                        listenerSocket.receive(receivePacket)
                        Log.d(tAG, "received: "
                                + receiveBuffer.copyOfRange(0, receivePacket.length).toString(Charsets.UTF_8))

                    }
                }
            } catch (e: InterruptedException) {
                // Restore interrupt status.
                Thread.currentThread().interrupt()
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            //stopSelf(msg.arg1)
        }
    }

    override fun onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }
        isRunning = true
        _uiState.value = isRunning
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        serviceHandler?.obtainMessage()?.also { msg ->
            msg.arg1 = startId
            msg.obj = intent
            serviceHandler?.sendMessage(msg)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        //Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
        isRunning = false
        _uiState.value = isRunning
    }
}