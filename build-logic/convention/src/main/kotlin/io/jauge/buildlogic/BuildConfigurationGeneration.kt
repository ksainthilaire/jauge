package io.jauge.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

/**
 * Registers the `generateBuildConfiguration` task on this project and adds its output to `commonMain`.
 *
 * @param packageName package of the generated `BuildConfiguration` object.
 */
fun Project.generateBuildConfiguration(packageName: String) {
    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
    val localPropertiesText = providers.fileContents(localPropertiesFile).asText.orElse("")

    val task = tasks.register<GenerateBuildConfigurationTask>("generateBuildConfiguration") {
        this.packageName.set(packageName)
        // Xcode exports CONFIGURATION: an iOS Release build never embeds the Test Store key nor the offline mode.
        releaseBuild.set(
            providers.environmentVariable("CONFIGURATION").map { it.equals("Release", ignoreCase = true) }.orElse(false),
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/buildConfiguration/commonMain/kotlin"))
        GenerateBuildConfigurationTask.ALL_KEYS.forEach { key ->
            val fromLocalProperties = localPropertiesText.map { text ->
                Properties().apply { load(text.reader()) }.getProperty(key).orEmpty()
            }
            values.put(
                key,
                providers.environmentVariable(GenerateBuildConfigurationTask.environmentVariableFor(key))
                    .orElse(fromLocalProperties),
            )
        }
    }

    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName("commonMain").kotlin.srcDir(task.map { it.outputDirectory })
    }
}
