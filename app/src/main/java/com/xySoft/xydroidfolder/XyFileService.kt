package com.xySoft.xydroidfolder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xySoft.xydroidfolder.comm.CmdPar
import com.xySoft.xydroidfolder.comm.CommData
import com.xySoft.xydroidfolder.comm.CommResult
import com.xySoft.xydroidfolder.comm.DroidFolderCmd
import com.xySoft.xydroidfolder.comm.DroidFolderComm
import com.xySoft.xydroidfolder.comm.FileInOut
import com.xySoft.xydroidfolder.comm.FileTransEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

class XyFileService : Service()  {
    val tAG: String = "XyFileService"

    private val job = SupervisorJob()
    private val receiveScope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        var instance: XyFileService? = null
        const val PC_ADDRESS = "pcAddress"

        private var droidFolderComm: DroidFolderComm? = null

        // The UI collects from this StateFlow to get its state updates
        val ServiceState: MutableStateFlow<XyFileServiceState> = MutableStateFlow(XyFileServiceState(
            messages = mutableListOf()
        ))

        fun changeRunningState(isRunning: Boolean){
            ServiceState.value = ServiceState.value.copy(isRunning = isRunning)
        }
        fun setConnectError(connectError: String?){
            ServiceState.value = ServiceState.value.copy(connectError = connectError)
        }
        fun setTargetPC(targetPC: String){
            ServiceState.value = ServiceState.value.copy(targetPC = targetPC)
        }
        fun addStateMessage(message: String){
            val messages = ServiceState.value.messages.toMutableList()
            messages.add(message)
            if(messages.size > 10){
                messages.removeFirst()
            }
            ServiceState.value = ServiceState.value.copy(
                messages = messages,
            )
        }

        fun stopService(){
            droidFolderComm?.clean()
            droidFolderComm = null
            cancelNotification()
            changeRunningState(false)
            ServiceState.value.messages.clear()
        }

        fun sendText(text: String) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try{
                    droidFolderComm?.sendText(text)
                    addStateMessage(instance!!.getString(R.string.send_succeed))
                }catch (e: Exception){
                    addStateMessage(instance!!.getString(R.string.send_failed, e.message))
                }
            }
        }

        //region: Persistent service icon in notification bar

        private const val NOTIFICATION: Int = 1
        private var mNotificationManager: NotificationManager? = null
        private fun cancelNotification() {
            mNotificationManager?.cancel(NOTIFICATION)
        }
        //endregion
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
                val targetAddress: String = intent.getStringExtra(PC_ADDRESS)!!

                val pcIp = targetAddress.split(":")[0]
                val pcPort = targetAddress.split(":")[1]

                val ipAddress: String? = getDeviceIpAddress()

                if (ipAddress != null) {
                    if(ipAddress.isNotEmpty()){
                        if(droidFolderComm == null){
                            droidFolderComm = DroidFolderComm(
                                ipAddress,12921,
                                pcIp, pcPort.toInt(),
                                receiveScope,
                                ::xyCommRequestHandler,
                                ::fileTransEventHandler
                            )
                        }
                        setConnectError(getString(R.string.connecting))
                        receiveScope.launch {
                            try{
                                val commResult = droidFolderComm!!.register(
                                    ipAddress, 12921,
                                    getDeviceName()
                                )
                                Log.d(tAG, "commResult: "
                                        + commResult.resultDataDic[CmdPar.returnMsg])
                                addStateMessage(
                                    getString(R.string.register_response)
                                            + commResult.resultDataDic[CmdPar.returnMsg])

                                setupNotifications()
                                showNotification(
                                    buildString {
                                        append(targetAddress)
                                        append(" ")
                                        append(commResult.resultDataDic[CmdPar.returnMsg])
                                    }
                                )
                                setTargetPC(targetAddress)
                                changeRunningState(true)
                            }catch (e: Exception){
                                droidFolderComm?.clean()
                                droidFolderComm = null
                                setConnectError(getString(R.string.connect_failed, e.message))
                            }
                        }
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
        instance = this
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
        instance = null
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }

    fun getDeviceIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intF = en.nextElement()
                val enumIpAddress = intF.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val inetAddress = enumIpAddress.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }

    fun xyCommRequestHandler(commData: CommData, commResult: CommResult){
        when(commData.cmd){
            DroidFolderCmd.GetInitFolder -> {
                addStateMessage(getString(R.string.init_folder))
            }
            DroidFolderCmd.GetFolder -> {
                addStateMessage(
                    getString(
                        R.string.list_request,
                        (commData.cmdParDic[CmdPar.requestPath]?.replace("\\", "/") ?: "")
                    ))
            }
            DroidFolderCmd.SendFile -> {
                addStateMessage(
                    getString(
                        R.string.receive_file,
                        (commData.cmdParDic[CmdPar.targetFile]?.replace("\\", "/") ?: "").split("/").last()
                    ))

                commResult.resultDataDic[CmdPar.streamReceiverPar] = "12922"
            }
            DroidFolderCmd.GetFile -> {
                addStateMessage(
                    getString(
                        R.string.send_file,
                        (commData.cmdParDic[CmdPar.targetFile]?.replace("\\", "/") ?: "").split("/").last()
                    ))

                commResult.resultDataDic[CmdPar.streamReceiverPar] = "12922"
            }
            DroidFolderCmd.SendText -> {
                val text = commData.cmdParDic[CmdPar.text]
                addStateMessage(getString(R.string.receive_text_copy_to_clipboard))

                // Creates a new text clip to put on the clipboard.
                val clip = ClipData.newPlainText("sync text", text)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(clip)
            }
            else -> {}
        }
    }
    fun fileTransEventHandler(
        fileTransEventType: FileTransEventType,
        fileInOut: FileInOut?,
        file: String?,
        totalLong: Long,
        doneLong: Long)
    {
        when(fileTransEventType){
            FileTransEventType.Start -> {
                val fileTransInfo = FileTransInfo(
                    isFileIn = (fileInOut == FileInOut.In),
                    file = file!!,
                    totalLong = totalLong
                )
                ServiceState.value = ServiceState.value.copy(
                    inFileTransfer = true,
                    fileProgress = 0,
                    fileTransInfo = fileTransInfo
                )
            }
            FileTransEventType.End -> {
                ServiceState.value = ServiceState.value.copy(
                    inFileTransfer = false,
                    fileProgress = 0,
                    fileTransInfo = null
                )
                addStateMessage(
                    if(fileInOut == FileInOut.In)
                        getString(
                            R.string.receive_file, getString(R.string.file_transfer_finish)
                        )
                    else
                        getString(
                            R.string.send_file, getString(R.string.file_transfer_finish)
                        )
                )
            }
            FileTransEventType.Progress -> {
                ServiceState.value = ServiceState.value.copy(
                    fileProgress = doneLong,
                )
            }
        }
    }

    //region: Persistent service icon in notification bar
    private val CLOSE_ACTION: String = "close"
    private val CHANNEL_ID: String = "my_channel_id_01"

    private var mNotificationBuilder: NotificationCompat.Builder? = null
    private fun setupNotifications() { //called in onCreate()
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pendingCloseIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction(CLOSE_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel()
        mNotificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)

        mNotificationBuilder!!
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle("Connect to PC")
            .setWhen(System.currentTimeMillis())
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Stop", pendingCloseIntent
            )
            .setOngoing(true)
    }

    private fun showNotification(message: String?) {
        mNotificationBuilder!!
            .setTicker(message)
            .setContentText(message)
        mNotificationManager?.notify(NOTIFICATION, mNotificationBuilder!!.build())
    }

    private fun createNotificationChannel(){
        // Create the NotificationChannel.
        val name = "myChannel"
        val descriptionText = "myChannel descriptionText"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }
    //endregion
}

data class XyFileServiceState(
    val isRunning: Boolean = false,
    val connectError: String? = null,
    val targetPC: String = "",
    val messages: MutableList<String>,
    val inFileTransfer: Boolean = false,
    val isFileIn: Boolean = false,
    val fileProgress: Long = 0,
    val fileTransInfo: FileTransInfo? = null
)
data class FileTransInfo(
    val isFileIn: Boolean = false,
    val file: String,
    val totalLong: Long
)