package io.jauge.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog declared in `gradle/libs.versions.toml`. */
val Project.libraries: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads a version from the catalog, failing loudly when it is missing. */
fun VersionCatalog.versionOf(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version '$alias' in libs.versions.toml") }.requiredVersion

/** Reads a library coordinate from the catalog, failing loudly when it is missing. */
fun VersionCatalog.libraryOf(alias: String) =
    findLibrary(alias).orElseThrow { IllegalStateException("Missing library '$alias' in libs.versions.toml") }

/**
 * Builds the Android namespace of a module from its Gradle path.
 * Example: `:core:designsystem` becomes `io.jauge.core.designsystem`. The shared app module `:composeApp` uses
 * `io.jauge.app`.
 */
val Project.jaugeNamespace: String
    get() = if (path == ":composeApp") "io.jauge.app" else "io.jauge" + path.replace(':', '.').replace('-', '.')
