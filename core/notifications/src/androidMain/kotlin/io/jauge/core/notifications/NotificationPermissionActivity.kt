package io.jauge.core.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Invisible activity that shows the Android 13 notification permission prompt, so that the shared code can ask the
 * permission without knowing the current screen. It finishes as soon as the user answers.
 */
class NotificationPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.complete(granted)
        pending = null
        finish()
    }

    /** Shows the system prompt once. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Answers "refused" if the activity is closed without an answer. */
    override fun onDestroy() {
        if (isFinishing) {
            pending?.complete(false)
            pending = null
        }
        super.onDestroy()
    }

    /** Entry point used by [FirebasePushService]. */
    companion object {
        private var pending: CompletableDeferred<Boolean>? = null

        /**
         * Shows the prompt and waits for the answer.
         *
         * @param context any context; the activity is started in a new task when it is not an activity.
         * @return true when the user allowed notifications.
         */
        suspend fun request(context: Context): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            pending?.complete(false)
            pending = deferred
            context.startActivityFromApplication(Intent(context, NotificationPermissionActivity::class.java))
            return deferred.await()
        }
    }
}
