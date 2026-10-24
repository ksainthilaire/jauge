import io.jauge.buildlogic.libraries
import io.jauge.buildlogic.libraryOf
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for `feature:*` modules (one module per user journey).
 *
 * A feature module gets Compose, type safe navigation (serializable routes), Koin view models and the core modules a
 * screen needs: design system, user interface helpers, domain and common. A feature never depends on another feature
 * nor on the data layer: navigation between features is wired in `composeApp` through callbacks.
 */
class FeatureConventionPlugin : Plugin<Project> {

    /** Applies the convention to [target]. */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jauge.compose.multiplatform")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(project(":core:common"))
                        implementation(project(":core:designsystem"))
                        implementation(project(":core:domain"))
                        implementation(libraries.libraryOf("koin-compose"))
                        implementation(libraries.libraryOf("koin-compose-viewmodel"))
                        implementation(libraries.libraryOf("androidx-lifecycle-viewmodel-compose"))
                        implementation(libraries.libraryOf("androidx-navigation-compose"))
                        implementation(libraries.libraryOf("kotlinx-serialization-json"))
                    }
                }
            }
        }
    }
}
