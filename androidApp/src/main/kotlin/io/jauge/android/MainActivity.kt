package io.jauge.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.jauge.app.JaugeApp
import io.jauge.app.link.IncomingLinks
import io.jauge.app.session.SessionSynchronizer
import org.koin.android.ext.android.get

/**
 * Single activity of the app: hosts the shared Compose user interface and receives the links opened from outside
 * (sign in callback of Supabase, public analysis links).
 */
class MainActivity : ComponentActivity() {

    /** Draws edge to edge and sets the shared root composable. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { JaugeApp() }
        if (savedInstanceState == null) openLink(intent)
    }

    /** Receives the links opened while the app is already running (`singleTask`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /**
     * Hands a link to the shared code: the sign in callback (`io.jauge.app://login-callback`) to the authentication,
     * an App Link (`https://jauge.io/a/{analysisId}`) to the navigation.
     */
    private fun openLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        if (!get<SessionSynchronizer>().handleDeepLink(url)) IncomingLinks.open(url)
    }
}
