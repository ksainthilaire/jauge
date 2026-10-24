package io.jauge.feature.account.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Logos of the sign in providers, drawn from the mockup 11 paths (brand colors kept as the providers require). */
object ProviderLogos {
    /** Apple logo, black, 24 x 24. */
    val Apple: ImageVector by lazy {
        ImageVector.Builder(name = "Apple", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(
                    "M16.4 12.7c0-2.5 2.1-3.7 2.2-3.8-1.2-1.7-3-2-3.7-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.3-.9-1.7 0-3.3 1-4.2 2.5" +
                        "-1.8 3.1-.5 7.8 1.3 10.3.9 1.2 1.9 2.6 3.2 2.6 1.3-.1 1.8-.8 3.3-.8 1.6 0 2 .8 3.4.8 1.4 0 2.3-1.3 3.1-2.5" +
                        " 1-1.4 1.4-2.8 1.4-2.9 0 0-2.8-1.1-2.8-4.2zM13.9 5.2c.7-.9 1.2-2 1-3.2-1 0-2.3.7-3 1.5-.7.7-1.2 1.9-1.1 3.1" +
                        " 1.2.1 2.3-.6 3.1-1.4z",
                ),
                fill = SolidColor(Color.Black),
            )
            .build()
    }

    /** Google "G" logo in its four colors, 48 x 48. */
    val Google: ImageVector by lazy {
        ImageVector.Builder(name = "Google", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 48f, viewportHeight = 48f)
            .addPath(
                pathData = addPathNodes(
                    "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22" +
                        "l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
                ),
                fill = SolidColor(Color(0xFFEA4335)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6" +
                        "c4.51-4.18 7.09-10.36 7.09-17.65z",
                ),
                fill = SolidColor(Color(0xFF4285F4)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24" +
                        "c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
                ),
                fill = SolidColor(Color(0xFFFBBC05)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91" +
                        "l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
                ),
                fill = SolidColor(Color(0xFF34A853)),
            )
            .build()
    }
}
