package io.jauge.feature.analysis.favorites

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.jauge.core.domain.error.JaugeError
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.favorite_add_failed
import io.jauge.feature.analysis.resources.favorite_remove_failed
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString

/**
 * A star change refused by the server, already undone on screen.
 *
 * @property wasAdding true when the user tried to add the star, false when they tried to remove it.
 * @property error the reason, never shown as is.
 */
data class FavoriteFailure(val wasAdding: Boolean, val error: JaugeError)

/**
 * Shows a friendly message in [snackbarHostState] for every [FavoriteFailure] ("L'étoile n'a pas pu être enregistrée").
 *
 * @param failures failures sent by the view model.
 * @param snackbarHostState host of the message.
 */
@Composable
fun FavoriteFailureMessages(failures: Flow<FavoriteFailure>, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(failures, snackbarHostState) {
        failures.collect { failure ->
            snackbarHostState.showSnackbar(getString(if (failure.wasAdding) Res.string.favorite_add_failed else Res.string.favorite_remove_failed))
        }
    }
}
