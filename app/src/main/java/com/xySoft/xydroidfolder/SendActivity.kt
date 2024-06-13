package com.xySoft.xydroidfolder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        XyFileService.sharedDataTask(intent)
        val myIntent = Intent(
            this,
            MainActivity::class.java
        )
        myIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        this.startActivity(myIntent)
        finish()
    }
}