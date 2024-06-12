package com.xySoft.xydroidfolder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xySoft.xydroidfolder.ui.theme.XyDroidFolderATheme
import java.io.File


class SendActivity : ComponentActivity() {
    private val tAG: String = "SendActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var initDataTask: String? = InitDataTask.NONE.toString()
        var initTaskPars: String? = null

        //var sharedText: String? = null
        Log.d(tAG, "intent?.action: ${intent?.action}")
        if(intent?.action == Intent.ACTION_SEND) {
            if (intent.type?.startsWith("text/") == true) {
                //sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                //Log.d(tAG, "Shared text from intent: $sharedText")
                initDataTask = InitDataTask.Text.toString()
                initTaskPars = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            else if (intent.type?.startsWith("image/") == true) {
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
                Log.d(tAG, "Shared image path from intent: ${uri?.path}")
                initDataTask = InitDataTask.File.toString()

                val directory =
                    Environment
                        .getExternalStorageDirectory()
                        .path.toString()

                val tempDirectory = File("$directory/temp")
                if (!(tempDirectory.exists() && tempDirectory.isDirectory)) {
                    tempDirectory.mkdir()
                }

                //temp file for send, need clean up after send
                initTaskPars = "$directory/temp/${uri?.path?.split("/")?.last()}"

                contentResolver.openInputStream(uri!!).use { input ->
                    File(initTaskPars).outputStream().use { output ->
                        input?.copyTo(output)
                    }
                }
                Log.d(tAG, "temple copped: $initTaskPars")
            }
            if(initDataTask != InitDataTask.NONE.toString()){
                if(XyFileService.ServiceState.value.isRunning) {
                    when (initDataTask) {
                        InitDataTask.Text.toString() -> {
                            XyFileService.sendText(initTaskPars!!)
                        }

                        InitDataTask.File.toString() -> {
                            XyFileService.sendFile(initTaskPars!!)
                        }
                    }
                }
                else{
                    XyFileService.initDataTask = initDataTask
                    XyFileService.initTaskPars = initTaskPars
                }

                val myIntent = Intent(
                    this,
                    MainActivity::class.java
                )
                this.startActivity(myIntent)
            }
        }
        finish()

        enableEdgeToEdge()
        setContent {
            XyDroidFolderATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Send shared content to pc",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    XyDroidFolderATheme {
        Greeting()
    }
}