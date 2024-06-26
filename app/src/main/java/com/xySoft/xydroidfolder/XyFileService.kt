package com.xySoft.xydroidfolder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.Process
import android.provider.OpenableColumns
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
import java.util.UUID

class XyFileService : Service()  {

    private val job = SupervisorJob()
    private val receiveScope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val tAG: String = "XyFileService"
        var instance: XyFileService? = null
        const val PC_ADDRESS = "pcAddress"

        fun sharedDataTask(intent: Intent){
            var dataTask: String? = InitDataTask.NONE.toString()
            var taskPars: String? = null

            Log.d(tAG, "Shared intent.type: ${intent.type}")
            if (intent.type?.startsWith("text/") == true) {
                dataTask = InitDataTask.Text.toString()
                taskPars = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d(tAG, "Shared text from intent: $taskPars")
            }
            else {
                var uri: Uri? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { //Android 13 sdk 33
                    (intent.getParcelableExtra(
                        Intent.EXTRA_STREAM, Parcelable::class.java) as? Uri)?.let {
                        // Update UI to reflect image being shared
                        uri = it
                    }
                }
                else {
                    (intent.getParcelableExtra<Parcelable>(
                        Intent.EXTRA_STREAM) as? Uri)?.let {
                        // Update UI to reflect image being shared
                        uri = it
                    }
                }
                if(uri != null){
                    dataTask = InitDataTask.File.toString()
                    taskPars = uri.toString()
                }
            }
            if(dataTask != InitDataTask.NONE.toString()){
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
//                    var tryTimes = 0  //when user not connect to pc, the permission still not granted
//                    while(!ServiceState.value.isRunning){
//                        delay(1000)
//                        tryTimes++
//                        if(tryTimes > 20){
//                            //if user not connect to pc in 20s, cancel share task
//                            break
//                        }
//                    }
                    if(ServiceState.value.isRunning){
                        when (dataTask) {
                            InitDataTask.Text.toString() -> {
                                sendText(taskPars!!)
                            }

                            InitDataTask.File.toString() -> {
                                sendFile(Uri.parse(taskPars)!!)
                            }
                        }
                    }
                }
            }
        }

        //get file size
        //code is copy from https://stackoverflow.com/questions/49415012/get-file-size-using-uri-in-android
        private fun Uri.length(context: Context): Long {
            val fromContentProviderColumn = fun(): Long {
                // Try to get content length from the content provider column OpenableColumns.SIZE
                // which is recommended to implement by all the content providers
                var cursor: Cursor? = null
                return try {
                    cursor = context.contentResolver.query(
                        this,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    ) ?: throw Exception("Content provider returned null or crashed")
                    val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumnIndex != -1 && cursor.count > 0) {
                        cursor.moveToFirst()
                        cursor.getLong(sizeColumnIndex)
                    } else {
                        -1
                    }
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    -1
                } finally {
                    cursor?.close()
                }
            }

            val fromFileDescriptor = fun(): Long {
                // Try to get content length from content scheme uri or file scheme uri
                var fileDescriptor: ParcelFileDescriptor? = null
                return try {
                    fileDescriptor = context.contentResolver.openFileDescriptor(this, "r")
                        ?: throw Exception("Content provider recently crashed")
                    fileDescriptor.statSize
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    -1
                } finally {
                    fileDescriptor?.close()
                }
            }

            val fromAssetFileDescriptor = fun(): Long {
                // Try to get content length from content scheme uri, file scheme uri or android resource scheme uri
                var assetFileDescriptor: AssetFileDescriptor? = null
                return try {
                    assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(this, "r")
                        ?: throw Exception("Content provider recently crashed")
                    assetFileDescriptor.length
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    -1
                } finally {
                    assetFileDescriptor?.close()
                }
            }

            return when (scheme) {
                ContentResolver.SCHEME_FILE -> {
                    fromFileDescriptor()
                }
                ContentResolver.SCHEME_CONTENT -> {
                    val length = fromContentProviderColumn()
                    if (length >= 0) {
                        length
                    } else {
                        fromFileDescriptor()
                    }
                }
                ContentResolver.SCHEME_ANDROID_RESOURCE -> {
                    fromAssetFileDescriptor()
                }
                else -> {
                    -1
                }
            }
        }
        private fun Uri.fileName(context: Context): String {
            val fromContentProviderColumn = fun(): String {
                // Try to get content length from the content provider column OpenableColumns.SIZE
                // which is recommended to implement by all the content providers
                var cursor: Cursor? = null
                return try {
                    cursor = context.contentResolver.query(
                        this,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    ) ?: throw Exception("Content provider returned null or crashed")
                    val nameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumnIndex != -1 && cursor.count > 0) {
                        cursor.moveToFirst()
                        cursor.getString(nameColumnIndex)
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    ""
                } finally {
                    cursor?.close()
                }
            }

            val fromFileDescriptor = fun(): String {
                // Try to get content length from content scheme uri or file scheme uri
                return try {
                    this.toString().split("/").last()
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    ""
                }
            }

            val fromAssetFileDescriptor = fun(): String {
                // Try to get content length from content scheme uri, file scheme uri or android resource scheme uri
                return try {
                    this.toString().split("/").last()
                } catch (e: Exception) {
                    Log.d(tAG, e.message ?: e.javaClass.simpleName)
                    ""
                }
            }

            return when (scheme) {
                ContentResolver.SCHEME_FILE -> {
                    fromFileDescriptor()
                }
                ContentResolver.SCHEME_CONTENT -> {
                    val fName = fromContentProviderColumn()
                    if (fName != "") {
                        fName
                    } else {
                        fromFileDescriptor()
                    }
                }
                ContentResolver.SCHEME_ANDROID_RESOURCE -> {
                    fromAssetFileDescriptor()
                }
                else -> {
                    ""
                }
            }
        }

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
            changeRunningState(false)
            setConnectError(instance!!.getString(R.string.disconnected))
            ServiceState.value.messages.clear()
            instance?.stopSelf()
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

        private fun sendFile(file: Uri) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try{
                    val inputStream = instance!!.contentResolver.openInputStream(file)
                    if(inputStream == null){
                        throw Exception("inputStream is null")
                    }
                    else{
                        val size = file.length(instance!!)
                        var fileName = file.fileName(instance!!)
                        if(fileName.isEmpty() || fileName==""){
                            fileName= UUID.randomUUID().toString()
                        }
                        droidFolderComm?.sendFile(inputStream, fileName, size)
                        addStateMessage(instance!!.getString(R.string.send_succeed))
                    }
                }catch (e: Exception){
                    //addStateMessage(instance!!.getString(R.string.send_failed, e.message))
                    addStateMessage("send_failed: " + e.message)
                    Log.d(tAG, "send_failed: " + e.stackTraceToString())
                }
            }
        }
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
                                setConnectError(null)
                            }catch (e: Exception){
                                Log.d(tAG, "Exception: "
                                        + e.message)
                                droidFolderComm?.clean()
                                droidFolderComm = null
                                setConnectError(getString(R.string.connect_failed, e.message))
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // Restore interrupt status.
                Log.d(tAG, "handleMessage error InterruptedException: " + e.message)
                Thread.currentThread().interrupt()
            }
            catch (e: Exception){
                Log.d(tAG, "handleMessage error Exception: " + e.message)
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
        Log.d(tAG, "Service: onCreate()")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()
        val action = intent.action
        if (action != null) {
            when (action) {
                CLOSE_ACTION -> {
                    stopService()
                }
            }
        }
        else{
            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job
            serviceHandler?.obtainMessage()?.also { msg ->
                msg.arg1 = startId
                msg.obj = intent
                serviceHandler?.sendMessage(msg)
            }
        }

        Log.d(tAG, "Service: onStartCommand()")
        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        Log.d(tAG, "Service: onBind()")
        return null
    }

    override fun onDestroy() {
        Log.d(tAG, "Service: onDestroy()")
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
    private val CLOSE_ACTION: String = "CLOSE_ACTION"
    private val CHANNEL_ID: String = "my_channel_id_01"

    private var mNotificationBuilder: NotificationCompat.Builder? = null
    private fun setupNotifications() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pendingCloseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, XyFileService::class.java)
                .setAction(CLOSE_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel()
        mNotificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)

        mNotificationBuilder!!
            .setSmallIcon(R.drawable.taskbaricon)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(getString(R.string.connect_information))
            .setWhen(System.currentTimeMillis())
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.taskbaricon,
                getString(R.string.stopService), pendingCloseIntent
            )
            .setOngoing(true)
    }

    private fun showNotification(message: String?) {
        mNotificationBuilder!!
            .setTicker(message)
            .setContentText(message)

        //this keep the service alive
        startForeground(
            16, //FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            mNotificationBuilder!!.build())
    }

    private fun createNotificationChannel(){
        // Create the NotificationChannel.
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description_text)
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

enum class InitDataTask {
    NONE,
    Text,
    File
}