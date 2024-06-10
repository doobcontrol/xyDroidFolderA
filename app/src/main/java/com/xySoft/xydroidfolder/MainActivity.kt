package com.xySoft.xydroidfolder

import android.Manifest.permission
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xySoft.xydroidfolder.ui.MainScreen
import com.xySoft.xydroidfolder.ui.theme.XyDroidFolderATheme

class MainActivity : ComponentActivity() {
    private val tAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { //Android 13 sdk 33
            pushNotificationPermissionLauncher.launch(permission.POST_NOTIFICATIONS)
        }
        if(!checkStoragePermissions()){
            requestForStoragePermissions()
        }

        var sharedText: String? = null
        if(intent?.action == Intent.ACTION_SEND) {
            if (intent.type?.startsWith("text/") == true) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d(tAG, "Shared text from intent: $sharedText")
            }
        }

        enableEdgeToEdge()
        setContent {
            XyDroidFolderATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        sharedText = sharedText,
                        startFileService = { startFileService(it) },
                        pasteToPc = ::pasteToPc,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkStoragePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11 (R) or above
            return Environment.isExternalStorageManager()
        } else {
            //Below android 11
            val write =
                ContextCompat.checkSelfPermission(this,
                    permission.WRITE_EXTERNAL_STORAGE)
            val read =
                ContextCompat.checkSelfPermission(this,
                    permission.READ_EXTERNAL_STORAGE)

            return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    private val storagePermissionCode = 23
    private fun requestForStoragePermissions() {
        //Android is 11 (R) or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent()
                intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.setData(uri)
                storageActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storageActivityResultLauncher.launch(intent)
            }
        } else {
            //Below android 11
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    permission.WRITE_EXTERNAL_STORAGE,
                    permission.READ_EXTERNAL_STORAGE
                ),
                storagePermissionCode
            )
        }
    }
    private val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(tAG, "$result")
        }
    private val pushNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { //granted ->

    }
    private fun startFileService(pcAddress: String){
        val intent = Intent(this, XyFileService::class.java)
        intent.putExtra(XyFileService.PC_ADDRESS, pcAddress)
        startService(Intent(intent))
    }
    private fun pasteToPc() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if(clipboard.hasPrimaryClip()
            && clipboard.primaryClipDescription?.hasMimeType(MIMETYPE_TEXT_PLAIN) == true
            ){
            val item = clipboard.primaryClip!!.getItemAt(0)

            val pasteText = item.text
            if (pasteText != null) {
                XyFileService.sendText(pasteText.toString())
            }
        }
    }
}