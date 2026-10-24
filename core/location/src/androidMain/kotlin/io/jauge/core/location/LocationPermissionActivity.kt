package io.jauge.core.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Invisible activity that shows the system prompt for `ACCESS_COARSE_LOCATION`, so that the shared code can ask the
 * permission after its own explanation without knowing the current screen. It finishes as soon as the user answers.
 */
class LocationPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.complete(granted)
        pending = null
        finish()
    }

    /** Shows the system prompt once. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** Answers "refused" if the activity is closed without an answer. */
    override fun onDestroy() {
        if (isFinishing) {
            pending?.complete(false)
            pending = null
        }
        super.onDestroy()
    }

    /** Entry point used by [AndroidPositionProvider]. */
    companion object {
        private var pending: CompletableDeferred<Boolean>? = null

        /**
         * Shows the prompt and waits for the answer.
         *
         * @param context any context; the activity is started in a new task when it is not an activity.
         * @return true when the user allowed the approximate position.
         */
        suspend fun request(context: Context): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            pending?.complete(false)
            pending = deferred
            context.startActivity(Intent(context, LocationPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return deferred.await()
        }
    }
}
