package io.jauge.core.designsystem.icon

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Line icons of the app, drawn from the SVG symbols of the mockups (24 x 24, stroke 2, round caps).
 * Use them with `androidx.compose.material3.Icon`, which tints them with the current content color.
 */
object JaugeIcons {
    /** Icon for a pasted link. */
    val Link: ImageVector by lazy { strokeIcon("Link", "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7 M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7") }

    /** Icon for a screenshot. */
    val Image: ImageVector by lazy { strokeIcon("Image", "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3z M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z M21 15l-5-5L5 21") }

    /** Icon for taking a photo. */
    val Camera: ImageVector by lazy { strokeIcon("Camera", "M4 8h3l2-3h6l2 3h3v11H4z M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z") }

    /** Icon for going back. */
    val Back: ImageVector by lazy { strokeIcon("Back", "m15 18-6-6 6-6") }

    /** Icon for sharing. */
    val Share: ImageVector by lazy { strokeIcon("Share", "M12 3v12 M7 8l5-5 5 5 M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6") }

    /** Icon for alerts. */
    val Bell: ImageVector by lazy { strokeIcon("Bell", "M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10 21a2 2 0 0 0 4 0") }

    /** Icon for a confirmed item. */
    val Check: ImageVector by lazy { strokeIcon("Check", "M20 6 9 17l-5-5") }

    /** Icon for closing. */
    val Close: ImageVector by lazy { strokeIcon("Close", "M18 6 6 18M6 6l12 12") }

    /** Icon for the analyze tab. */
    val Gauge: ImageVector by lazy { strokeIcon("Gauge", "M4 17a8 8 0 1 1 16 0 M12 17l4-5") }

    /** Icon for the watch tab. */
    val Radar: ImageVector by lazy { strokeIcon("Radar", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z M12 12L18 6") }

    /** Icon for the profile tab. */
    val Profile: ImageVector by lazy { strokeIcon("Profile", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z M4 21a8 8 0 0 1 16 0") }

    /** Icon for a place. */
    val Pin: ImageVector by lazy { strokeIcon("Pin", "M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z M9.5 9a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z") }

    /** Icon for a salary. */
    val Euro: ImageVector by lazy { strokeIcon("Euro", "M18 6.5A7 7 0 1 0 18 17.5 M4 10h10M4 14h10") }

    /** Icon for going forward. */
    val ArrowRight: ImageVector by lazy { strokeIcon("ArrowRight", "M5 12h14 M13 6l6 6-6 6") }

    /** Icon for a rising trend. */
    val TrendUp: ImageVector by lazy { strokeIcon("TrendUp", "m3 17 6-6 4 4 8-8 M15 7h6v6") }

    /** Icon for copying. */
    val Copy: ImageVector by lazy { strokeIcon("Copy", "M10 8h9a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-9a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2z M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3") }

    /** Icon for saving. */
    val Download: ImageVector by lazy { strokeIcon("Download", "M12 3v12 M7 10l5 5 5-5 M5 21h14") }

    /** Icon for messages. */
    val Message: ImageVector by lazy { strokeIcon("Message", "M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.9A8 8 0 1 1 21 12z") }

    /** Icon for timing advice. */
    val Clock: ImageVector by lazy { strokeIcon("Clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M12 7v5l3 2") }

    /** Icon for information. */
    val Info: ImageVector by lazy { strokeIcon("Info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M12 11v6 M12 7.5v.01") }

    /** Icon for a warning. */
    val Warning: ImageVector by lazy { strokeIcon("Warning", "M12 3 2 20h20z M12 10v4 M12 17v.01") }

    /** Icon for opening a row. */
    val ChevronRight: ImageVector by lazy { strokeIcon("ChevronRight", "m9 6 6 6-6 6") }

    /** Icon for signing out. */
    val SignOut: ImageVector by lazy { strokeIcon("SignOut", "M15 4h4v16h-4 M10 8l-4 4 4 4 M6 12h10") }

    /** Icon for deleting. */
    val Trash: ImageVector by lazy { strokeIcon("Trash", "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13") }

    /** Icon for a document. */
    val Document: ImageVector by lazy { strokeIcon("Document", "M6 3h8l4 4v14H6z M14 3v4h4 M9 13h6M9 17h6") }

    /** Icon for privacy. */
    val Shield: ImageVector by lazy { strokeIcon("Shield", "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z") }

    /** Icon for email. */
    val Mail: ImageVector by lazy { strokeIcon("Mail", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z M3 7l9 6 9-6") }

    /** Icon for a locked feature. */
    val Lock: ImageVector by lazy { strokeIcon("Lock", "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z M8 11V7a4 4 0 0 1 8 0v4") }

    /** Empty star: the analysis is not a favorite. */
    val Star: ImageVector by lazy { strokeIcon("Star", "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z") }

    /** Magnifying glass of the search fields. */
    val Search: ImageVector by lazy { strokeIcon("Search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z M20 20l-4-4") }

    /** Filled star: the analysis is a favorite. */
    val StarFilled: ImageVector by lazy { filledIcon("StarFilled", "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z") }
}

/**
 * Builds a filled 24 x 24 icon from SVG path data (same path drawn with a stroke too, so it has the size of its line
 * version).
 *
 * @param name name of the vector, for debugging.
 * @param pathData SVG path data.
 */
private fun filledIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/**
 * Builds a stroked 24 x 24 icon from SVG path data.
 *
 * @param name name of the vector, for debugging.
 * @param pathData SVG path data.
 */
private fun strokeIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
