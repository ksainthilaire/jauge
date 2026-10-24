package io.jauge.feature.analysis.picker

import androidx.core.content.FileProvider

/**
 * File provider of the photos taken for an analysis. A subclass of its own, so that it does not clash with the file
 * providers of other modules in the merged manifest.
 */
class AnalysisFileProvider : FileProvider()
