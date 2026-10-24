import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.jauge.buildlogic.jaugeNamespace
import io.jauge.buildlogic.libraries
import io.jauge.buildlogic.libraryOf
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for shared modules that contain Compose Multiplatform user interface code.
 *
 * It applies the Compose Multiplatform and Compose compiler plugins, enables Android resources (needed by Compose
 * resources such as `strings.xml` and fonts), adds the Compose dependencies and generates a module specific `Res`
 * class in the `<namespace>.resources` package.
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {

    /** Applies the convention to [target]. */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jauge.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
                    androidResources {
                        enable = true
                    }
                }
                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(libraries.libraryOf("compose-runtime"))
                        implementation(libraries.libraryOf("compose-foundation"))
                        implementation(libraries.libraryOf("compose-ui"))
                        implementation(libraries.libraryOf("compose-material3"))
                        implementation(libraries.libraryOf("compose-components-resources"))
                        implementation(libraries.libraryOf("compose-ui-toolingPreview"))
                        implementation(libraries.libraryOf("androidx-lifecycle-runtime-compose"))
                    }
                }
            }

            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                packageOfResClass = "$jaugeNamespace.resources"
                publicResClass = false
                generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            }
        }
    }
}
