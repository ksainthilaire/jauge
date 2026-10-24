package io.jauge.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.jauge.app.JaugeApp

/** Single activity of the app: hosts the shared Compose user interface. */
class MainActivity : ComponentActivity() {

    /** Draws edge to edge and sets the shared root composable. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { JaugeApp() }
    }
}
